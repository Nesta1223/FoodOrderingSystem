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

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order " + orderId + " not found"));
    }

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
