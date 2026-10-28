package orderpayment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for paying, looking up and refunding payments (PaymentController).
 */
class PaymentApiTest extends ApiTestSupport {

    private static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    // A fresh PENDING order of 2 x 100.50 = 201.00 for every test
    private long orderId;

    @BeforeEach
    void createUnpaidOrder() throws Exception {
        long restaurantId = createRestaurant("Jay Fai", "ALL_DAY");
        long omelette = addMenuItem(restaurantId, "Crab Omelette", "100.50");
        orderId = createOrder(somchai(), restaurantId, omelette, 2);
    }

    private ResultActions refund(long paymentId) throws Exception {
        return mockMvc.perform(post("/api/payments/" + paymentId + "/refund"));
    }

    // ---------------------------------------------------------------- POST /api/payments

    /**
     * Paying the exact total succeeds immediately (there is no external gateway): the payment is SUCCESS,
     * gets a UUID transaction reference and a paid time, and records the method used.
     */
    @Test
    void payInFull() throws Exception {
        pay(orderId, "201.00", "WALLET")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.amount").value(201.00))
                .andExpect(jsonPath("$.method").value("WALLET"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionRef", matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.paidAt", notNullValue()));
    }

    /**
     * The amount is compared as money, not as text: 201 equals 201.00. The method is accepted in any case.
     */
    @Test
    void amountIsComparedByValue() throws Exception {
        pay(orderId, "201", "credit_card")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("CREDIT_CARD"));
    }

    /** Paying the same order twice is refused, so a customer is never charged twice. */
    @Test
    void cannotPayTwice() throws Exception {
        pay(orderId, "201.00", "WALLET").andExpect(status().isCreated());

        pay(orderId, "201.00", "CASH")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order already paid"));
    }

    /**
     * The amount must match the total exactly. One satang too little or too much is refused, so there are
     * no partial payments or overpayments.
     */
    @Test
    void amountMustMatchTotalExactly() throws Exception {
        pay(orderId, "200.99", "WALLET")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment amount does not match order total"));
        pay(orderId, "201.01", "WALLET")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment amount does not match order total"));
    }

    /** A cancelled order cannot be paid. */
    @Test
    void cannotPayCancelledOrder() throws Exception {
        mockMvc.perform(delete("/api/orders/" + orderId)).andExpect(status().isOk());

        pay(orderId, "201.00", "WALLET")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot pay for a cancelled order"));
    }

    /** An unknown payment method is refused, and the message lists the allowed ones. */
    @Test
    void rejectsUnknownPaymentMethod() throws Exception {
        pay(orderId, "201.00", "BITCOIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid method: BITCOIN")));
    }

    /** A missing order id, a negative amount and a blank method are each reported per field. */
    @Test
    void rejectsInvalidRequest() throws Exception {
        postJson("/api/payments", "{\"amount\": -5, \"method\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.orderId").value("orderId is required"))
                .andExpect(jsonPath("$.fieldErrors.amount").value("amount must be positive"))
                .andExpect(jsonPath("$.fieldErrors.method").value("method is required"));
    }

    /** Paying for an order that does not exist returns 404. */
    @Test
    void payUnknownOrder() throws Exception {
        pay(UNKNOWN_ID, "10.00", "CASH")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id " + UNKNOWN_ID + " not found"));
    }

    // ---------------------------------------------------------------- GET /api/payments/{orderId}

    /** After paying, the order's payment can be looked up and shows the same transaction reference. */
    @Test
    void getPaymentOfPaidOrder() throws Exception {
        String reference = (String) jsonAt(pay(orderId, "201.00", "WALLET"), "$.transactionRef");

        mockMvc.perform(get("/api/payments/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionRef").value(reference));
    }

    /**
     * An order that exists but was never paid returns 404 with a payment-specific message, while an order
     * that does not exist returns 404 for the order, so the caller can tell the two apart.
     */
    @Test
    void getPaymentNotFoundCases() throws Exception {
        mockMvc.perform(get("/api/payments/" + orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment for order " + orderId + " not found"));
        mockMvc.perform(get("/api/payments/" + UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id " + UNKNOWN_ID + " not found"));
    }

    // ---------------------------------------------------------------- POST /api/payments/{id}/refund

    /**
     * Refunding keeps the payment as a record: only the status changes to REFUNDED, while the amount,
     * transaction reference and paid time stay exactly as they were.
     */
    @Test
    void refundKeepsThePaymentRecord() throws Exception {
        long paymentId = idOf(pay(orderId, "201.00", "WALLET"));
        // Read the payment as stored, so the comparison uses the database's timestamp precision
        ResultActions stored = mockMvc.perform(get("/api/payments/" + orderId));
        String reference = (String) jsonAt(stored, "$.transactionRef");
        String paidAt = (String) jsonAt(stored, "$.paidAt");

        refund(paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.amount").value(201.00))
                .andExpect(jsonPath("$.transactionRef").value(reference))
                .andExpect(jsonPath("$.paidAt").value(paidAt));
    }

    /** Only a successful payment can be refunded, so the same payment cannot be refunded twice. */
    @Test
    void cannotRefundTwice() throws Exception {
        long paymentId = idOf(pay(orderId, "201.00", "WALLET"));
        refund(paymentId).andExpect(status().isOk());

        refund(paymentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only successful payments can be refunded"));
    }

    /** Refunding a payment that does not exist returns 404. */
    @Test
    void refundUnknownPayment() throws Exception {
        refund(UNKNOWN_ID)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Payment with id " + UNKNOWN_ID + " not found"));
    }

    /**
     * After a refund the order counts as unpaid again, so it can be paid anew. The new payment gets its own
     * reference, and looking up the order's payment returns the newest one.
     */
    @Test
    void refundedOrderCanBePaidAgain() throws Exception {
        ResultActions first = pay(orderId, "201.00", "WALLET");
        String firstReference = (String) jsonAt(first, "$.transactionRef");
        refund(idOf(first)).andExpect(status().isOk());

        long secondId = idOf(pay(orderId, "201.00", "CASH").andExpect(status().isCreated()));

        mockMvc.perform(get("/api/payments/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondId))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.transactionRef").value(not(firstReference)));
    }

    /** After a refund the order is unpaid again, so a PENDING order can now be cancelled. */
    @Test
    void refundedOrderCanBeCancelled() throws Exception {
        refund(idOf(pay(orderId, "201.00", "WALLET"))).andExpect(status().isOk());

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
