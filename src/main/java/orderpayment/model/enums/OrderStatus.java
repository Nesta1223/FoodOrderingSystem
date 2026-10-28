package orderpayment.model.enums;

/**
 * The life of an order. It moves forward only: PENDING, CONFIRMED, PREPARING, DELIVERED.
 * CANCELLED is a separate ending that is only possible from PENDING.
 */
public enum OrderStatus {

    /**
     * The order has just been placed and the restaurant has not accepted it yet. This is the starting status.
     * It is the only status from which the order can be cancelled, and the order can be paid while in it.
     */
    PENDING,

    /** The restaurant has accepted the order. It can no longer be cancelled. */
    CONFIRMED,

    /** The restaurant is cooking or preparing the order. */
    PREPARING,

    /** The order has reached the customer. This is a final status. */
    DELIVERED,

    /**
     * The order was cancelled while still PENDING. This is a final status. A cancelled order is kept for
     * history, cannot be paid, and cannot be cancelled again.
     */
    CANCELLED
}
