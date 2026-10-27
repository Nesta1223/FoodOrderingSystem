package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.dto.request.CreateMenuItemRequest;
import com.pbgold.orderpayment.dto.request.CreateRestaurantRequest;
import com.pbgold.orderpayment.dto.request.OpeningHoursRequest;
import com.pbgold.orderpayment.dto.request.UpdateOpeningHoursRequest;
import com.pbgold.orderpayment.dto.response.MenuItemResponse;
import com.pbgold.orderpayment.dto.response.OpeningHoursResponse;
import com.pbgold.orderpayment.dto.response.RestaurantResponse;
import com.pbgold.orderpayment.exception.BusinessException;
import com.pbgold.orderpayment.exception.ResourceNotFoundException;
import com.pbgold.orderpayment.model.MenuItem;
import com.pbgold.orderpayment.model.OpeningHours;
import com.pbgold.orderpayment.model.Restaurant;
import com.pbgold.orderpayment.model.enums.RestaurantCategory;
import com.pbgold.orderpayment.repository.MenuItemRepository;
import com.pbgold.orderpayment.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OpeningHoursChecker openingHoursChecker;

    public RestaurantService(RestaurantRepository restaurantRepository,
                             MenuItemRepository menuItemRepository,
                             OpeningHoursChecker openingHoursChecker) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.openingHoursChecker = openingHoursChecker;
    }

    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName().trim())
                .category(EnumParser.parse(RestaurantCategory.class, request.getCategory(), "category"))
                .isOpen(true)
                .build();
        if (request.getOpeningHours() != null) {
            restaurant.replaceOpeningHours(toOpeningHours(request.getOpeningHours()));
        }
        return toRestaurantResponse(restaurantRepository.save(restaurant));
    }

    // Only restaurants that are open right now: manual switch on AND inside today's opening hours
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAllOpenRestaurants() {
        return restaurantRepository.findByIsOpenTrueOrderByNameAsc().stream()
                .filter(openingHoursChecker::isOpenNow)
                .map(this::toRestaurantResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpeningHoursResponse> getOpeningHours(Long restaurantId) {
        return toOpeningHoursResponses(findRestaurant(restaurantId));
    }

    // Replaces the whole schedule, so the client always sends the complete week
    @Transactional
    public RestaurantResponse updateOpeningHours(Long restaurantId, UpdateOpeningHoursRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        restaurant.replaceOpeningHours(toOpeningHours(request.getOpeningHours()));
        return toRestaurantResponse(restaurantRepository.saveAndFlush(restaurant));
    }

    @Transactional
    public RestaurantResponse toggleAcceptingOrders(Long restaurantId) {
        Restaurant restaurant = findRestaurant(restaurantId);
        restaurant.setIsOpen(!Boolean.TRUE.equals(restaurant.getIsOpen()));
        return toRestaurantResponse(restaurant);
    }

    @Transactional
    public MenuItemResponse addMenuItem(Long restaurantId, CreateMenuItemRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        MenuItem menuItem = MenuItem.builder()
                .restaurant(restaurant)
                .name(request.getName().trim())
                // @Digits guarantees at most 2 decimals, so this only pads (250 -> 250.00)
                .price(request.getPrice().setScale(2, RoundingMode.UNNECESSARY))
                .isAvailable(true)
                .build();
        return toMenuItemResponse(menuItemRepository.save(menuItem));
    }

    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(Long restaurantId) {
        findRestaurant(restaurantId);
        return menuItemRepository.findByRestaurantIdOrderByIdAsc(restaurantId).stream()
                .map(this::toMenuItemResponse)
                .toList();
    }

    @Transactional
    public MenuItemResponse toggleAvailability(Long menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", menuItemId));
        menuItem.setIsAvailable(!Boolean.TRUE.equals(menuItem.getIsAvailable()));
        return toMenuItemResponse(menuItem);
    }

    private Restaurant findRestaurant(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
    }

    private List<OpeningHours> toOpeningHours(List<OpeningHoursRequest> requests) {
        return requests.stream().map(r -> {
            if (r.getOpenTime().equals(r.getCloseTime())) {
                throw new BusinessException("openTime and closeTime must differ for " + r.getDayOfWeek());
            }
            return OpeningHours.builder()
                    .dayOfWeek(EnumParser.parse(DayOfWeek.class, r.getDayOfWeek(), "dayOfWeek"))
                    .openTime(r.getOpenTime())
                    .closeTime(r.getCloseTime())
                    .build();
        }).toList();
    }

    private List<OpeningHoursResponse> toOpeningHoursResponses(Restaurant restaurant) {
        return restaurant.getOpeningHours().stream()
                .sorted(Comparator.comparing(OpeningHours::getDayOfWeek).thenComparing(OpeningHours::getOpenTime))
                .map(h -> OpeningHoursResponse.builder()
                        .dayOfWeek(h.getDayOfWeek().name())
                        .openTime(h.getOpenTime())
                        .closeTime(h.getCloseTime())
                        .build())
                .toList();
    }

    private RestaurantResponse toRestaurantResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .category(restaurant.getCategory().name())
                .acceptingOrders(restaurant.getIsOpen())
                .openNow(openingHoursChecker.isOpenNow(restaurant))
                .openingHours(toOpeningHoursResponses(restaurant))
                .createdAt(restaurant.getCreatedAt())
                .build();
    }

    private MenuItemResponse toMenuItemResponse(MenuItem menuItem) {
        return MenuItemResponse.builder()
                .id(menuItem.getId())
                .restaurantId(menuItem.getRestaurant().getId())
                .name(menuItem.getName())
                .price(menuItem.getPrice())
                .isAvailable(menuItem.getIsAvailable())
                .build();
    }
}
