package com.pbgold.orderpayment.controller;

import com.pbgold.orderpayment.dto.request.CreateMenuItemRequest;
import com.pbgold.orderpayment.dto.request.CreateRestaurantRequest;
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

    @PostMapping("/restaurants")
    @Operation(summary = "Create a restaurant")
    public ResponseEntity<RestaurantResponse> createRestaurant(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.createRestaurant(request));
    }

    @GetMapping("/restaurants")
    @Operation(summary = "List restaurants that are open right now (switch on and inside opening hours)")
    public ResponseEntity<List<RestaurantResponse>> getAllOpenRestaurants() {
        return ResponseEntity.ok(restaurantService.getAllOpenRestaurants());
    }

    @GetMapping("/restaurants/{id}/opening-hours")
    @Operation(summary = "Get a restaurant's weekly opening hours")
    public ResponseEntity<List<OpeningHoursResponse>> getOpeningHours(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getOpeningHours(id));
    }

    @PutMapping("/restaurants/{id}/opening-hours")
    @Operation(summary = "Replace a restaurant's whole opening-hours schedule (empty list = no restriction)")
    public ResponseEntity<RestaurantResponse> updateOpeningHours(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateOpeningHoursRequest request) {
        return ResponseEntity.ok(restaurantService.updateOpeningHours(id, request));
    }

    @PatchMapping("/restaurants/{id}/accepting-orders")
    @Operation(summary = "Toggle the manual switch that pauses or resumes orders, regardless of opening hours")
    public ResponseEntity<RestaurantResponse> toggleAcceptingOrders(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.toggleAcceptingOrders(id));
    }

    @PostMapping("/restaurants/{id}/menu")
    @Operation(summary = "Add a menu item to a restaurant")
    public ResponseEntity<MenuItemResponse> addMenuItem(@PathVariable Long id,
                                                        @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.addMenuItem(id, request));
    }

    @GetMapping("/restaurants/{id}/menu")
    @Operation(summary = "Get a restaurant's menu")
    public ResponseEntity<List<MenuItemResponse>> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getMenu(id));
    }

    @PatchMapping("/menu/{id}/availability")
    @Operation(summary = "Toggle a menu item's availability")
    public ResponseEntity<MenuItemResponse> toggleAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.toggleAvailability(id));
    }
}
