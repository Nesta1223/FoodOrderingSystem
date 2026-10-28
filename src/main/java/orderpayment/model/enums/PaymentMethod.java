package orderpayment.model.enums;

/**
 * How the customer pays. It is recorded for reporting and accounting. There is no external payment gateway,
 * so the method does not change how a payment is processed.
 */
public enum PaymentMethod {

    /** Paid from the customer's wallet balance. */
    WALLET,

    /** Paid by credit card. */
    CREDIT_CARD,

    /** Paid in cash, for example to the rider on delivery. */
    CASH
}
