package orderpayment.model.enums;

/**
 * What happened to a payment. A payment row is never deleted, so its status is the history of the money.
 */
public enum PaymentStatus {

    /**
     * The payment was started but not finished. Reserved for a future payment gateway: today a valid payment
     * is saved directly as SUCCESS, so nothing creates this status yet.
     */
    PENDING,

    /**
     * The money was taken in full. An order can have at most one payment in this status, which is how the
     * system knows the order is paid. Only a payment in this status can be refunded.
     */
    SUCCESS,

    /**
     * The payment attempt failed. Reserved for a future payment gateway: nothing creates this status yet.
     * A failed payment does not count as paying the order.
     */
    FAILED,

    /**
     * A successful payment that was given back to the customer. The row, amount, transaction reference and
     * paid time are kept as a record. Once refunded the order counts as unpaid again, so it can be paid anew.
     */
    REFUNDED
}
