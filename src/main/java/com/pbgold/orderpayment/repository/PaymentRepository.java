package com.pbgold.orderpayment.repository;

import com.pbgold.orderpayment.model.Payment;
import com.pbgold.orderpayment.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    // An order can have several payment rows (e.g. a refunded one, then a new one); return the latest
    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);
}
