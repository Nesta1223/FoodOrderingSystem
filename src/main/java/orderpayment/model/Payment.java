package orderpayment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import orderpayment.model.enums.PaymentMethod;
import orderpayment.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    /**
     * Unique identifier of the payment, assigned by the database. Used to refund this specific payment.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this payment pays for. Required. An order can have several payment rows over time (for
     * example a refunded payment, then a new successful one), but never more than one with status SUCCESS.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * The money charged. It must equal the order's total exactly, so partial payments are not supported.
     * Kept unchanged after a refund, so the record still shows what was originally paid.
     * Stored as DECIMAL(10,2).
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * How the customer paid: WALLET, CREDIT_CARD or CASH. Recorded for reporting and accounting. There is no
     * external payment gateway, so the method does not change how the payment is processed.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /**
     * Where the payment stands: PENDING, SUCCESS, FAILED or REFUNDED. A valid payment is saved straight as
     * SUCCESS, and a refund later changes SUCCESS to REFUNDED. The row is never deleted, so the status is
     * the history of what happened to the money.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * A unique reference (a random UUID) generated when the payment succeeds. Support, accounting or a
     * future payment gateway can use it to point at exactly one payment. Unique across all payments.
     */
    @Column(name = "transaction_ref", unique = true, length = 100)
    private String transactionRef;

    /**
     * When the payment succeeded. Kept after a refund as the record of when the money was originally taken.
     * Empty for a payment that never succeeded.
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
