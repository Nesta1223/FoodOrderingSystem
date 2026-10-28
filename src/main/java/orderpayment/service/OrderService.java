package orderpayment.service;

import orderpayment.dto.request.CreateOrderItemRequest;
import orderpayment.dto.request.CreateOrderRequest;
import orderpayment.dto.request.UpdateOrderStatusRequest;
import orderpayment.dto.response.OrderItemResponse;
import orderpayment.dto.response.OrderResponse;
import orderpayment.exception.BusinessException;
import orderpayment.exception.ResourceNotFoundException;
import orderpayment.model.MenuItem;
import orderpayment.model.Order;
import orderpayment.model.OrderItem;
import orderpayment.model.Restaurant;
import orderpayment.model.User;
import orderpayment.model.enums.OrderStatus;
import orderpayment.model.enums.PaymentStatus;
import orderpayment.repository.MenuItemRepository;
import orderpayment.repository.OrderRepository;
import orderpayment.repository.PaymentRepository;
import orderpayment.repository.RestaurantRepository;
import orderpayment.repository.UserRepository;
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

    /**
     * Creates a PENDING order from item ids and quantities.
     * 1. The user and the restaurant must exist.
     * 2. The restaurant must be open now: not paused and inside its opening hours.
     * 3. Every item must exist, belong to that restaurant and be available.
     * 4. Each item's current menu price is copied into its order line, so later price changes
     *    never change this order.
     * 5. The total is calculated here as the sum of quantity x locked price. Nothing from the client is trusted.
     * Any failure throws before anything is saved, so no half-built order is left behind.
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUserId()));
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", request.getRestaurantId()));

        // Rule 1: restaurant must be open now (not paused AND inside its opening hours)
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

    /**
     * Loads one order with its items, or throws 404 if it does not exist.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long id) {
        return toResponse(findOrder(id));
    }

    /**
     * Lists a user's orders, newest first. An unknown user throws 404, so it is not confused
     * with a real user who simply has no orders yet.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Moves an order to the requested status. The order row is locked first, so a payment or a cancel
     * arriving at the same moment cannot interleave. CANCELLED goes through the cancel rules. Any other
     * target must be the next step of PENDING, CONFIRMED, PREPARING, DELIVERED; skipping a step or
     * moving backwards throws 400.
     */
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

    /**
     * Cancels an order after locking its row. Allowed only while the order is PENDING and has no successful
     * payment. The order is kept and marked CANCELLED, never deleted.
     */
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
