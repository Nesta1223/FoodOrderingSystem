package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.dto.request.CreateOrderItemRequest;
import com.pbgold.orderpayment.dto.request.CreateOrderRequest;
import com.pbgold.orderpayment.dto.request.UpdateOrderStatusRequest;
import com.pbgold.orderpayment.dto.response.OrderItemResponse;
import com.pbgold.orderpayment.dto.response.OrderResponse;
import com.pbgold.orderpayment.exception.BusinessException;
import com.pbgold.orderpayment.exception.ResourceNotFoundException;
import com.pbgold.orderpayment.model.MenuItem;
import com.pbgold.orderpayment.model.Order;
import com.pbgold.orderpayment.model.OrderItem;
import com.pbgold.orderpayment.model.enums.OrderStatus;
import com.pbgold.orderpayment.model.enums.PaymentStatus;
import com.pbgold.orderpayment.model.Restaurant;
import com.pbgold.orderpayment.model.User;
import com.pbgold.orderpayment.repository.MenuItemRepository;
import com.pbgold.orderpayment.repository.OrderRepository;
import com.pbgold.orderpayment.repository.PaymentRepository;
import com.pbgold.orderpayment.repository.RestaurantRepository;
import com.pbgold.orderpayment.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    // Forward-only lifecycle. Cancellation is only possible from PENDING (business rule 5).
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final PaymentRepository paymentRepository;
    private final OpeningHoursChecker openingHoursChecker;

    public OrderService(OrderRepository orderRepository,
                        UserRepository userRepository,
                        RestaurantRepository restaurantRepository,
                        MenuItemRepository menuItemRepository,
                        PaymentRepository paymentRepository,
                        OpeningHoursChecker openingHoursChecker) {
        this.openingHoursChecker = openingHoursChecker;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.getRestaurantId()));

        // Rule 1: restaurant must be open now (manual switch on AND inside its opening hours)
        if (!openingHoursChecker.isOpenNow(restaurant)) {
            throw new BusinessException("Restaurant is closed");
        }

        Set<Long> menuItemIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getMenuItemId)
                .collect(Collectors.toSet());
        Map<Long, MenuItem> menuItems = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));

        Order order = Order.builder()
                .user(user)
                .restaurant(restaurant)
                .status(OrderStatus.PENDING)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : request.getItems()) {
            MenuItem menuItem = menuItems.get(itemRequest.getMenuItemId());
            if (menuItem == null) {
                throw new ResourceNotFoundException("MenuItem", itemRequest.getMenuItemId());
            }
            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BusinessException("MenuItem " + menuItem.getId()
                        + " does not belong to restaurant " + restaurant.getId());
            }
            // Rule 2: every item must be available
            if (!Boolean.TRUE.equals(menuItem.getIsAvailable())) {
                throw new BusinessException("MenuItem " + menuItem.getId() + " is not available");
            }

            // Rule 4: lock the price by copying it, not referencing the live menu price
            OrderItem orderItem = OrderItem.builder()
                    .menuItem(menuItem)
                    .quantity(itemRequest.getQuantity())
                    .priceAtOrder(menuItem.getPrice())
                    .build();
            order.addItem(orderItem);

            // Rule 3: total is computed server-side only
            total = total.add(orderItem.getSubtotal());
        }
        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

        return toResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return toResponse(findOrder(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        OrderStatus target = EnumParser.parse(OrderStatus.class, request.getStatus(), "status");
        Order order = findOrderForUpdate(id);

        if (target == OrderStatus.CANCELLED) {
            return cancel(order);
        }
        if (!ALLOWED_TRANSITIONS.get(order.getStatus()).contains(target)) {
            throw new BusinessException("Cannot change order status from " + order.getStatus() + " to " + target);
        }
        order.setStatus(target);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public OrderResponse cancelOrder(Long id) {
        return cancel(findOrderForUpdate(id));
    }

    private OrderResponse cancel(Order order) {
        // Rule 5: only PENDING orders can be cancelled
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Cannot cancel order with status: " + order.getStatus());
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException("Cannot cancel a paid order; refund the payment first");
        }
        order.setStatus(OrderStatus.CANCELLED);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // Same row lock PaymentService takes, so a cancel and a payment cannot interleave
    private Order findOrderForUpdate(Long id) {
        return orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .menuItemId(item.getMenuItem().getId())
                        .name(item.getMenuItem().getName())
                        .quantity(item.getQuantity())
                        .priceAtOrder(item.getPriceAtOrder())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .restaurantId(order.getRestaurant().getId())
                .restaurantName(order.getRestaurant().getName())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .items(items)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
