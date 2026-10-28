package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.dto.request.CreateMenuItemRequest;
import com.pbgold.orderpayment.dto.request.CreateRestaurantRequest;
import com.pbgold.orderpayment.dto.request.PauseRestaurantRequest;
import com.pbgold.orderpayment.dto.request.UpdateOpeningHoursRequest;
import com.pbgold.orderpayment.dto.response.MenuItemResponse;
import com.pbgold.orderpayment.dto.response.OpeningHoursResponse;
import com.pbgold.orderpayment.dto.response.RestaurantResponse;
import com.pbgold.orderpayment.exception.ResourceNotFoundException;
import com.pbgold.orderpayment.model.MenuItem;
import com.pbgold.orderpayment.model.Restaurant;
import com.pbgold.orderpayment.model.enums.OpeningHours;
import com.pbgold.orderpayment.model.enums.RestaurantCategory;
import com.pbgold.orderpayment.repository.MenuItemRepository;
import com.pbgold.orderpayment.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    /**
     * Registers a restaurant. Category and opening hours arrive as text and are converted to enums
     * (an invalid value throws 400). Opening hours default to ALL_DAY when omitted. The name is trimmed.
     * A new restaurant is not paused.
     */
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request) {
        Restaurant restaurant = Restaurant.builder()
                .name(request.getName().trim())
                .category(EnumParser.parse(RestaurantCategory.class, request.getCategory(), "category"))
                .build();
        if (request.getOpeningHours() != null && !request.getOpeningHours().isBlank()) {
            restaurant.setOpeningHours(EnumParser.parse(OpeningHours.class, request.getOpeningHours(), "openingHours"));
        }
        return toRestaurantResponse(restaurantRepository.save(restaurant));
    }

    /**
     * Returns the restaurants open right now, sorted by name. It loads all restaurants and keeps those that
     * are not paused and whose opening hours include the current time. "Open" is calculated on every call
     * and never stored, so it cannot go out of date.
     */
    @Transactional(readOnly = true)
    public List<RestaurantResponse> getAllOpenRestaurants() {
        return restaurantRepository.findAllByOrderByNameAsc().stream()
                .filter(openingHoursChecker::isOpenNow)
                .map(this::toRestaurantResponse)
                .toList();
    }

    /**
     * Returns the restaurant's opening-hours preset with its open and close time. Throws 404 if the
     * restaurant does not exist.
     */
    @Transactional(readOnly = true)
    public OpeningHoursResponse getOpeningHours(Long restaurantId) {
        return toOpeningHoursResponse(findRestaurant(restaurantId).getOpeningHours());
    }

    /**
     * Replaces the restaurant's opening-hours preset. It applies to every day of the week and affects
     * only orders placed from now on. An invalid preset name throws 400.
     */
    @Transactional
    public RestaurantResponse updateOpeningHours(Long restaurantId, UpdateOpeningHoursRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        restaurant.setOpeningHours(EnumParser.parse(OpeningHours.class, request.getOpeningHours(), "openingHours"));
        return toRestaurantResponse(restaurant);
    }

    /**
     * Pauses the restaurant until now + durationMinutes and records the optional reason (blank counts as none).
     * Idempotent: calling it again just sets a new end time, it never switches the pause off.
     * While paused the restaurant counts as closed even inside its opening hours. When the end time passes,
     * the restaurant follows its opening hours again by itself, with no cleanup needed.
     */
    @Transactional
    public RestaurantResponse pauseRestaurant(Long restaurantId, PauseRestaurantRequest request) {
        Restaurant restaurant = findRestaurant(restaurantId);
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? null : request.getReason().trim();
        restaurant.pause(openingHoursChecker.now().plusMinutes(request.getDurationMinutes()), reason);
        return toRestaurantResponse(restaurant);
    }

    /**
     * Ends a pause early by clearing the end time and the reason. Idempotent: resuming a restaurant
     * that is not paused changes nothing.
     */
    @Transactional
    public RestaurantResponse resumeRestaurant(Long restaurantId) {
        Restaurant restaurant = findRestaurant(restaurantId);
        restaurant.resume();
        return toRestaurantResponse(restaurant);
    }

    /**
     * Adds an available menu item to a restaurant. The name is trimmed and the price is padded to 2 decimals.
     * Throws 404 if the restaurant does not exist.
     */
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

    /**
     * Returns every menu item of a restaurant, including unavailable ones. An unknown restaurant throws 404,
     * so it is not mistaken for a restaurant with an empty menu.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getMenu(Long restaurantId) {
        findRestaurant(restaurantId);
        return menuItemRepository.findByRestaurantIdOrderByIdAsc(restaurantId).stream()
                .map(this::toMenuItemResponse)
                .toList();
    }

    /**
     * Flips a menu item between available and unavailable. Unavailable items are rejected when ordering;
     * orders that already contain the item are not changed.
     */
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

    private OpeningHoursResponse toOpeningHoursResponse(OpeningHours openingHours) {
        return OpeningHoursResponse.builder()
                .type(openingHours.name())
                .openTime(openingHours.getOpenTime())
                .closeTime(openingHours.getCloseTime())
                .build();
    }

    private RestaurantResponse toRestaurantResponse(Restaurant restaurant) {
        LocalDateTime now = openingHoursChecker.now();
        boolean paused = restaurant.isPausedAt(now);
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .category(restaurant.getCategory().name())
                .acceptingOrders(!paused)
                .openNow(openingHoursChecker.isOpenAt(restaurant, now))
                // An expired pause is hidden, even though the old values stay in the row
                .pausedUntil(paused ? restaurant.getPausedUntil() : null)
                .pauseReason(paused ? restaurant.getPauseReason() : null)
                .openingHours(toOpeningHoursResponse(restaurant.getOpeningHours()))
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
