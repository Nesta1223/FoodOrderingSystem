package com.pbgold.orderpayment.controller;

import com.pbgold.orderpayment.dto.request.CreatePaymentRequest;
import com.pbgold.orderpayment.dto.response.PaymentResponse;
import com.pbgold.orderpayment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * API type: POST
     *
     * Pays for an order in full. The order must not be cancelled or already paid, and the amount must equal
     * the order total exactly. There is no external payment gateway, so a valid payment succeeds immediately
     * and receives a UUID transaction reference and a paid timestamp.
     */
    @PostMapping
    @Operation(summary = "Pay for an order (amount must equal the order total exactly)")
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    /**
     * API type: GET
     *
     * Returns the most recent payment for an order. An order can have several payment rows over time
     * (for example a refunded one, then a new one). Responds 404 if the order has never been paid.
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "Get the latest payment for an order")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrder(orderId));
    }

    /**
     * API type: POST
     *
     * Refunds a successful payment. Only SUCCESS payments can be refunded. The payment row is never deleted:
     * only its status changes to REFUNDED, so the history stays intact. The order itself is not changed.
     */
    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a successful payment (the record is kept, status becomes REFUNDED)")
    public ResponseEntity<PaymentResponse> refundPayment(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.refundPayment(id));
    }
}
