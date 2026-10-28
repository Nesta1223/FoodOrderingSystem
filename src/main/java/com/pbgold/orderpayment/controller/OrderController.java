package com.pbgold.orderpayment.controller;

import com.pbgold.orderpayment.dto.request.CreateOrderRequest;
import com.pbgold.orderpayment.dto.request.UpdateOrderStatusRequest;
import com.pbgold.orderpayment.dto.response.OrderResponse;
import com.pbgold.orderpayment.service.OrderService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * API type: POST
     *
     * Places a new PENDING order. The restaurant must be open now and every item must exist, belong to that
     * restaurant and be available. The client sends only item ids and quantities: the server copies each
     * item's current price into the order and calculates the total itself.
     */
    @PostMapping
    @Operation(summary = "Create an order (total is calculated server-side, prices are locked)")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    /**
     * API type: GET
     *
     * Returns one order with its items. Each item shows the price locked at order time and its subtotal.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get an order by id")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * API type: GET all
     *
     * Returns all orders of one user, newest first. Responds 404 if the user does not exist.
     */
    @GetMapping
    @Operation(summary = "List a user's orders, newest first")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@RequestParam Long userId) {
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    /**
     * API type: PUT / PATCH (PATCH)
     *
     * Moves an order forward through its lifecycle: PENDING, CONFIRMED, PREPARING, DELIVERED.
     * Skipping a step or going backwards is rejected. Setting CANCELLED follows the same rules as DELETE.
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Move an order to the next status (PENDING → CONFIRMED → PREPARING → DELIVERED)")
    public ResponseEntity<OrderResponse> updateOrderStatus(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }

    /**
     * API type: DELETE
     *
     * Cancels an order. Only PENDING orders can be cancelled, and not once they have been paid
     * (refund the payment first). The order is kept with status CANCELLED, it is not deleted.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel an order (only allowed while PENDING)")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
