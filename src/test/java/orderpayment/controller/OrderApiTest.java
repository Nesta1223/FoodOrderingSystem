package orderpayment.controller;

import orderpayment.model.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for placing, reading, progressing and cancelling orders (OrderController).
 * The clock is frozen at Monday 12:00, see {@link ApiTestSupport#NOW}.
 */
class OrderApiTest extends ApiTestSupport {

    // An ALL_DAY restaurant (open at noon) with two dishes, created fresh for every test
    private long restaurantId;
    private long omelette;  // 100.50
    private long tomYum;    // 250.00

    @BeforeEach
    void createOpenRestaurantWithMenu() throws Exception {
        restaurantId = createRestaurant("Jay Fai", "ALL_DAY");
        omelette = addMenuItem(restaurantId, "Crab Omelette", "100.50");
        tomYum = addMenuItem(restaurantId, "Tom Yum", "250");
    }

    // ---------------------------------------------------------------- POST /api/orders

    /**
     * Placing an order: it starts as PENDING, each line copies the dish's current price, and the server
     * calculates the total itself (2 x 100.50 + 1 x 250.00 = 451.00). The client tries to send a total of 1,
     * which is ignored, because a customer must never be able to choose what they pay.
     */
    @Test
    void createOrderCalculatesTotalOnServer() throws Exception {
        String json = """
                {"userId": %d, "restaurantId": %d, "totalAmount": 1,
                 "items": [{"menuItemId": %d, "quantity": 2}, {"menuItemId": %d, "quantity": 1}]}
                """.formatted(somchai(), restaurantId, omelette, tomYum);

        postJson("/api/orders", json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.userId").value(somchai()))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.restaurantName").value("Jay Fai"))
                .andExpect(jsonPath("$.totalAmount").value(451.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].name").value("Crab Omelette"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].priceAtOrder").value(100.50))
                .andExpect(jsonPath("$.items[0].subtotal").value(201.00))
                .andExpect(jsonPath("$.items[1].subtotal").value(250.00));
    }

    /**
     * Price locking: after the order is placed the restaurant raises the dish's price to 999.00, but the
     * order keeps the price the customer agreed to, and its total does not change.
     */
    @Test
    void orderKeepsThePriceFromWhenItWasPlaced() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 2);

        MenuItem dish = menuItemRepository.findById(omelette).orElseThrow();
        dish.setPrice(new BigDecimal("999.00"));
        menuItemRepository.save(dish);

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].priceAtOrder").value(100.50))
                .andExpect(jsonPath("$.totalAmount").value(201.00));
    }

    /** A restaurant outside its opening hours refuses orders. EVENING (17:00-23:00) is closed at noon. */
    @Test
    void createOrderRejectedOutsideOpeningHours() throws Exception {
        long dinnerPlace = createRestaurant("Dinner Place", "EVENING");
        long steak = addMenuItem(dinnerPlace, "Steak", "500");

        placeOrder(somchai(), dinnerPlace, steak, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Restaurant is closed"));
    }

    /** A paused restaurant refuses orders, even inside its opening hours. */
    @Test
    void createOrderRejectedWhileRestaurantIsPaused() throws Exception {
        pause(restaurantId, 30);

        placeOrder(somchai(), restaurantId, omelette, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Restaurant is closed"));
    }

    /** A sold-out (unavailable) dish cannot be ordered, and the error names the dish. */
    @Test
    void createOrderRejectsUnavailableItem() throws Exception {
        mockMvc.perform(patch("/api/menu/" + tomYum + "/availability"));

        placeOrder(somchai(), restaurantId, tomYum, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("MenuItem " + tomYum + " is not available"));
    }

    /**
     * A failed order leaves nothing behind. The order mixes an available dish with a sold-out one, it is
     * rejected, and the customer has no orders at all: no half-built order was saved.
     */
    @Test
    void rejectedOrderSavesNothing() throws Exception {
        mockMvc.perform(patch("/api/menu/" + tomYum + "/availability"));
        String json = """
                {"userId": %d, "restaurantId": %d,
                 "items": [{"menuItemId": %d, "quantity": 1}, {"menuItemId": %d, "quantity": 1}]}
                """.formatted(somchai(), restaurantId, omelette, tomYum);

        postJson("/api/orders", json).andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(somchai())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /** An order may only contain dishes from the restaurant it is placed at. */
    @Test
    void createOrderRejectsDishFromAnotherRestaurant() throws Exception {
        long otherRestaurant = createRestaurant("Other Place", "ALL_DAY");
        long otherDish = addMenuItem(otherRestaurant, "Noodles", "60");

        placeOrder(somchai(), restaurantId, otherDish, 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("MenuItem " + otherDish + " does not belong to restaurant " + restaurantId));
    }

    /** An unknown user, restaurant or dish returns 404 naming what was not found. */
    @Test
    void createOrderWithUnknownReferences() throws Exception {
        placeOrder(UNKNOWN_ID, restaurantId, omelette, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with id " + UNKNOWN_ID + " not found"));
        placeOrder(somchai(), UNKNOWN_ID, omelette, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Restaurant with id " + UNKNOWN_ID + " not found"));
        placeOrder(somchai(), restaurantId, UNKNOWN_ID, 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MenuItem with id " + UNKNOWN_ID + " not found"));
    }

    /** Missing ids, an empty item list and a quantity below 1 are rejected, each reported per field. */
    @Test
    void createOrderRejectsInvalidRequest() throws Exception {
        postJson("/api/orders", "{\"items\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").value("userId is required"))
                .andExpect(jsonPath("$.fieldErrors.restaurantId").value("restaurantId is required"))
                .andExpect(jsonPath("$.fieldErrors.items").value("items must not be empty"));

        placeOrder(somchai(), restaurantId, omelette, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("quantity must be at least 1"));
    }

    // ---------------------------------------------------------------- GET /api/orders/{id}

    /** An order can be read back with its lines and total. */
    @Test
    void getOrder() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, tomYum, 3);

        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items[0].name").value("Tom Yum"))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(750.00));
    }

    /** Reading an order that does not exist returns 404. */
    @Test
    void getUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/orders/" + UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id " + UNKNOWN_ID + " not found"));
    }

    // ---------------------------------------------------------------- GET /api/orders?userId=

    /** A customer sees only their own orders, never another customer's. */
    @Test
    void ordersByUserReturnsOnlyThatUsersOrders() throws Exception {
        long first = createOrder(somchai(), restaurantId, omelette, 1);
        long second = createOrder(somchai(), restaurantId, tomYum, 1);
        createOrder(malee(), restaurantId, omelette, 1);

        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(somchai())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder((int) first, (int) second)))
                .andExpect(jsonPath("$[*].userId", everyItem(is((int) somchai()))));
    }

    /**
     * A real customer with no orders gets an empty list, while an unknown customer gets 404, so the two
     * cases are never confused. A missing userId parameter is a 400.
     */
    @Test
    void ordersByUserEdgeCases() throws Exception {
        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(malee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/orders").param("userId", String.valueOf(UNKNOWN_ID)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request parameter"));
    }

    // ---------------------------------------------------------------- PATCH /api/orders/{id}/status

    /** The normal life of an order: PENDING, CONFIRMED, PREPARING, DELIVERED, one step at a time. */
    @Test
    void statusMovesForwardOneStepAtATime() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);

        for (String next : new String[] {"CONFIRMED", "PREPARING", "DELIVERED"}) {
            patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"" + next + "\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(next));
        }
    }

    /** Skipping a step is refused: a PENDING order cannot jump straight to DELIVERED. */
    @Test
    void statusCannotSkipAStep() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);

        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"DELIVERED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot change order status from PENDING to DELIVERED"));
    }

    /** Going backwards is refused: a CONFIRMED order cannot return to PENDING. */
    @Test
    void statusCannotGoBackwards() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);
        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"CONFIRMED\"}");

        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"PENDING\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot change order status from CONFIRMED to PENDING"));
    }

    /** A status name that does not exist is rejected, and a blank one fails validation. */
    @Test
    void statusRejectsUnknownValue() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);

        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"FLYING\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid status: FLYING")));
        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("status is required"));
    }

    /** Setting the status to CANCELLED works like cancelling: allowed for a PENDING order. */
    @Test
    void statusCancelledFollowsTheCancelRules() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);

        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"CANCELLED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ---------------------------------------------------------------- DELETE /api/orders/{id}

    /**
     * Cancelling a PENDING order marks it CANCELLED. The order is not deleted: it can still be read
     * afterwards, so the customer's history stays complete.
     */
    @Test
    void cancelPendingOrderKeepsItAsCancelled() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /** Once the restaurant has confirmed an order, it can no longer be cancelled. */
    @Test
    void cannotCancelConfirmedOrder() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);
        patchJson("/api/orders/" + orderId + "/status", "{\"status\": \"CONFIRMED\"}");

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot cancel order with status: CONFIRMED"));
    }

    /** Cancelling twice is refused, because a CANCELLED order is final. */
    @Test
    void cannotCancelTwice() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 1);
        mockMvc.perform(delete("/api/orders/" + orderId));

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot cancel order with status: CANCELLED"));
    }

    /** A paid order cannot be cancelled, even while it is still PENDING, so money is never left orphaned. */
    @Test
    void cannotCancelPaidOrder() throws Exception {
        long orderId = createOrder(somchai(), restaurantId, omelette, 2);
        pay(orderId, "201.00", "WALLET").andExpect(status().isCreated());

        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Cannot cancel a paid order")));
    }

    /** Cancelling an order that does not exist returns 404. */
    @Test
    void cancelUnknownOrder() throws Exception {
        mockMvc.perform(delete("/api/orders/" + UNKNOWN_ID))
                .andExpect(status().isNotFound());
    }
}
