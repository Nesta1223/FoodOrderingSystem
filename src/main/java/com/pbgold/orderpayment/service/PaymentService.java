package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.dto.request.CreatePaymentRequest;
import com.pbgold.orderpayment.dto.response.PaymentResponse;
import com.pbgold.orderpayment.exception.BusinessException;
import com.pbgold.orderpayment.exception.ResourceNotFoundException;
import com.pbgold.orderpayment.model.Order;
import com.pbgold.orderpayment.model.enums.OrderStatus;
import com.pbgold.orderpayment.model.Payment;
import com.pbgold.orderpayment.model.enums.PaymentMethod;
import com.pbgold.orderpayment.model.enums.PaymentStatus;
import com.pbgold.orderpayment.repository.OrderRepository;
import com.pbgold.orderpayment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Pays an order in full.
     * 1. Locks the order row, so two simultaneous requests cannot both pay the same order.
     * 2. Rejects cancelled orders, orders that already have a SUCCESS payment, and any amount that
     *    differs from the order total.
     * 3. There is no external gateway, so the payment is recorded as SUCCESS straight away with a random
     *    UUID transaction reference and the paid time.
     * The stored amount is the order total, never a value taken from the client.
     */
    @Transactional
    public PaymentResponse processPayment(CreatePaymentRequest request) {
        PaymentMethod method = EnumParser.parse(PaymentMethod.class, request.getMethod(), "method");

        // Row lock on the order so two concurrent requests cannot both pass the "already paid" check
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", request.getOrderId()));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Cannot pay for a cancelled order");
        }
        // Rule 6: an order can only have one successful payment
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)) {
            throw new BusinessException("Order already paid");
        }
        // Rule 7: amount must match exactly (compareTo so 100 and 100.00 are equal)
        if (request.getAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new BusinessException("Payment amount does not match order total");
        }

        // No external gateway here, so payment is treated as successful immediately.
        // Rule 8: successful payments get a UUID reference and a paid timestamp.
        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .method(method)
                .status(PaymentStatus.SUCCESS)
                .transactionRef(UUID.randomUUID().toString())
                .paidAt(LocalDateTime.now())
                .build();

        return toResponse(paymentRepository.save(payment));
    }

    /**
     * Returns the latest payment row of an order. Throws 404 if the order does not exist, and also
     * if it exists but has never been paid.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order " + orderId + " not found"));
    }

    /**
     * Refunds a payment. Only SUCCESS payments qualify. The row is never deleted: only its status changes
     * to REFUNDED, so the amount, transaction reference and paid time stay as an audit record.
     * The order itself is not changed.
     */
    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // Rule 9: only successful payments can be refunded
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BusinessException("Only successful payments can be refunded");
        }
        // Rule 10: the row is kept as an immutable record; only the status changes
        payment.setStatus(PaymentStatus.REFUNDED);
        return toResponse(payment);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .amount(payment.getAmount())
                .method(payment.getMethod().name())
                .status(payment.getStatus().name())
                .transactionRef(payment.getTransactionRef())
                .paidAt(payment.getPaidAt())
                .build();
    }
}
