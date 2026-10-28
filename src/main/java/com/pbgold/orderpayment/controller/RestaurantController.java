package com.pbgold.orderpayment.controller;

import com.pbgold.orderpayment.dto.request.CreateMenuItemRequest;
import com.pbgold.orderpayment.dto.request.CreateRestaurantRequest;
import com.pbgold.orderpayment.dto.request.PauseRestaurantRequest;
import com.pbgold.orderpayment.dto.request.UpdateOpeningHoursRequest;
import com.pbgold.orderpayment.dto.response.MenuItemResponse;
import com.pbgold.orderpayment.dto.response.OpeningHoursResponse;
import com.pbgold.orderpayment.dto.response.RestaurantResponse;
import com.pbgold.orderpayment.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Mapped at /api because the availability endpoint lives under /api/menu, not /api/restaurants
@RestController
@RequestMapping("/api")
@Tag(name = "Restaurants & Menu")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    /**
     * Registers a new restaurant. The category must be FOOD, DRINK or DESSERT. Opening hours are optional
     * and default to ALL_DAY. A new restaurant is not paused, so it is open whenever its opening hours allow.
     */
    @PostMapping("/restaurants")
    @Operation(summary = "Create a restaurant")
    public ResponseEntity<RestaurantResponse> createRestaurant(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createRestaurant(request));
    }

    /**
     * Lists only the restaurants a customer can order from right now: not paused, and the current time
     * (in the app time zone) is inside the restaurant's opening hours. Closed or paused restaurants are left out.
     */
    @GetMapping("/restaurants")
    @Operation(summary = "List restaurants that are open right now (not paused and inside opening hours)")
    public ResponseEntity<List<RestaurantResponse>> getAllOpenRestaurants() {
        return ResponseEntity.ok(restaurantService.getAllOpenRestaurants());
    }

    /**
     * Returns the restaurant's opening-hours preset with its open and close time (e.g. OFFICE_HOURS 09:00-21:00).
     * A close time earlier than the open time means the window runs past midnight.
     */
    @GetMapping("/restaurants/{id}/opening-hours")
    @Operation(summary = "Get a restaurant's opening hours (daily preset with its open and close time)")
    public ResponseEntity<OpeningHoursResponse> getOpeningHours(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getOpeningHours(id));
    }

    /**
     * Changes the restaurant's opening-hours preset. The preset applies to every day of the week and takes
     * effect immediately for new orders. Orders that were already placed are not affected.
     */
    @PutMapping("/restaurants/{id}/opening-hours")
    @Operation(summary = "Set a restaurant's opening hours preset (ALL_DAY, MORNING, OFFICE_HOURS, EVENING, LATE_NIGHT)")
    public ResponseEntity<RestaurantResponse> updateOpeningHours(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateOpeningHoursRequest request) {
        return ResponseEntity.ok(restaurantService.updateOpeningHours(id, request));
    }

    /**
     * Temporarily stops the restaurant from taking orders for 1-1440 minutes, with an optional reason.
     * The pause ends by itself. Calling it again only moves the end time, it never un-pauses.
     * A pause can only close a restaurant: it never opens one outside its opening hours.
     */
    @PutMapping("/restaurants/{id}/pause")
    @Operation(summary = "Pause orders for 1-1440 minutes; the pause ends by itself (repeat to extend)")
    public ResponseEntity<RestaurantResponse> pauseRestaurant(@PathVariable Long id,
                                                              @Valid @RequestBody PauseRestaurantRequest request) {
        return ResponseEntity.ok(restaurantService.pauseRestaurant(id, request));
    }

    /**
     * Ends a pause early so the restaurant follows its opening hours again.
     * Safe to repeat: resuming a restaurant that is not paused changes nothing.
     */
    @DeleteMapping("/restaurants/{id}/pause")
    @Operation(summary = "End a pause early and resume taking orders")
    public ResponseEntity<RestaurantResponse> resumeRestaurant(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.resumeRestaurant(id));
    }

    /**
     * Adds a dish to the restaurant's menu. It starts as available. The price is stored with 2 decimals;
     * orders copy the price at the moment they are placed, so it is not a promise for later orders.
     */
    @PostMapping("/restaurants/{id}/menu")
    @Operation(summary = "Add a menu item to a restaurant")
    public ResponseEntity<MenuItemResponse> addMenuItem(@PathVariable Long id,
                                                        @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.addMenuItem(id, request));
    }

    /**
     * Returns every menu item of the restaurant in the order they were added, including unavailable ones.
     */
    @GetMapping("/restaurants/{id}/menu")
    @Operation(summary = "Get a restaurant's menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getMenu(id));
    }

    /**
     * Flips a menu item between available and unavailable (e.g. sold out). Unavailable items cannot be ordered.
     * Orders that already contain the item are not affected.
     */
    @PatchMapping("/menu/{id}/availability")
    @Operation(summary = "Toggle a menu item's availability")
    public ResponseEntity<MenuItemResponse> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.toggleAvailability(id));
    }
}
