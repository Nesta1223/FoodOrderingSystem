package orderpayment.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API tests for restaurants, opening hours, pausing and menus (RestaurantController).
 * The clock is frozen at Monday 12:00, see {@link ApiTestSupport#NOW}.
 */
class RestaurantApiTest extends ApiTestSupport {

    // ---------------------------------------------------------------- POST /api/restaurants

    /**
     * Creating a restaurant with only the required fields: the name is trimmed, the category is accepted in
     * any case, opening hours default to ALL_DAY, and the new restaurant is open and accepting orders.
     */
    @Test
    void createRestaurantWithDefaults() throws Exception {
        postJson("/api/restaurants", "{\"name\": \"  Jay Fai  \", \"category\": \"food\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Jay Fai"))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.openingHours.type").value("ALL_DAY"))
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.openNow").value(true))
                .andExpect(jsonPath("$.pausedUntil").doesNotExist())
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    /**
     * Creating a restaurant with an opening-hours preset. EVENING is 17:00 to 23:00, so at noon the
     * restaurant exists and is accepting orders, but is not open right now.
     */
    @Test
    void createRestaurantWithOpeningHoursPreset() throws Exception {
        postJson("/api/restaurants", "{\"name\": \"Dinner Place\", \"category\": \"FOOD\", \"openingHours\": \"evening\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.openingHours.type").value("EVENING"))
                .andExpect(jsonPath("$.openingHours.openTime").value(startsWith("17:00")))
                .andExpect(jsonPath("$.openingHours.closeTime").value(startsWith("23:00")))
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.openNow").value(false));
    }

    /** An unknown category is rejected with 400, and the message lists the allowed values. */
    @Test
    void createRestaurantRejectsUnknownCategory() throws Exception {
        postJson("/api/restaurants", "{\"name\": \"X\", \"category\": \"PIZZA\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid category: PIZZA")))
                .andExpect(jsonPath("$.message").value(containsString("FOOD, DRINK, DESSERT")));
    }

    /** An unknown opening-hours preset is rejected with 400. */
    @Test
    void createRestaurantRejectsUnknownOpeningHours() throws Exception {
        postJson("/api/restaurants", "{\"name\": \"X\", \"category\": \"FOOD\", \"openingHours\": \"WHENEVER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid openingHours: WHENEVER")));
    }

    /** Blank required fields are reported together, one entry per field, in the standard error format. */
    @Test
    void createRestaurantRejectsBlankFields() throws Exception {
        postJson("/api/restaurants", "{\"name\": \"\", \"category\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("category is required"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /** A name longer than the 100-character database column is rejected before it reaches the database. */
    @Test
    void createRestaurantRejectsTooLongName() throws Exception {
        String longName = "x".repeat(101);
        postJson("/api/restaurants", "{\"name\": \"" + longName + "\", \"category\": \"FOOD\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("name must be at most 100 characters"));
    }

    /** A body that is not valid JSON gets a clean 400 instead of a server error. */
    @Test
    void createRestaurantRejectsMalformedJson() throws Exception {
        postJson("/api/restaurants", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request"));
    }

    // ---------------------------------------------------------------- GET /api/restaurants

    /**
     * The restaurant list shows only restaurants a customer can order from right now, sorted by name.
     * At noon: ALL_DAY and OFFICE_HOURS are listed, EVENING is left out (outside its hours), and a paused
     * ALL_DAY restaurant is left out (paused).
     */
    @Test
    void listShowsOnlyRestaurantsOpenRightNow() throws Exception {
        createRestaurant("Lunch Place", "OFFICE_HOURS");
        createRestaurant("Always Open", "ALL_DAY");
        createRestaurant("Dinner Place", "EVENING");
        long paused = createRestaurant("Paused Place", "ALL_DAY");
        pause(paused, 30);

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Always Open", "Lunch Place")));
    }

    /** When no restaurant is open the list is empty, not an error. */
    @Test
    void listIsEmptyWhenNothingIsOpen() throws Exception {
        createRestaurant("Dinner Place", "EVENING");

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------------------------------------------------------------- GET/PUT /api/restaurants/{id}/opening-hours

    /** Returns the restaurant's preset together with its open and close time. */
    @Test
    void getOpeningHours() throws Exception {
        long id = createRestaurant("Lunch Place", "OFFICE_HOURS");

        mockMvc.perform(get("/api/restaurants/" + id + "/opening-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("OFFICE_HOURS"))
                .andExpect(jsonPath("$.openTime").value(startsWith("09:00")))
                .andExpect(jsonPath("$.closeTime").value(startsWith("21:00")));
    }

    /** Asking for the opening hours of a restaurant that does not exist returns 404. */
    @Test
    void getOpeningHoursOfUnknownRestaurant() throws Exception {
        mockMvc.perform(get("/api/restaurants/" + UNKNOWN_ID + "/opening-hours"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Resource not found"))
                .andExpect(jsonPath("$.message").value("Restaurant with id " + UNKNOWN_ID + " not found"));
    }

    /**
     * Changing the preset takes effect immediately: an EVENING restaurant is closed at noon, and after
     * switching it to ALL_DAY it is open and appears in the restaurant list.
     */
    @Test
    void updateOpeningHoursTakesEffectImmediately() throws Exception {
        long id = createRestaurant("Dinner Place", "EVENING");

        putJson("/api/restaurants/" + id + "/opening-hours", "{\"openingHours\": \"ALL_DAY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingHours.type").value("ALL_DAY"))
                .andExpect(jsonPath("$.openNow").value(true));

        mockMvc.perform(get("/api/restaurants"))
                .andExpect(jsonPath("$[*].name", contains("Dinner Place")));
    }

    /** An unknown preset, a blank value and an unknown restaurant are each rejected. */
    @Test
    void updateOpeningHoursRejectsBadInput() throws Exception {
        long id = createRestaurant("Lunch Place", "OFFICE_HOURS");

        putJson("/api/restaurants/" + id + "/opening-hours", "{\"openingHours\": \"NOPE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Invalid openingHours: NOPE")));
        putJson("/api/restaurants/" + id + "/opening-hours", "{\"openingHours\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.openingHours").value("openingHours is required"));
        putJson("/api/restaurants/" + UNKNOWN_ID + "/opening-hours", "{\"openingHours\": \"ALL_DAY\"}")
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- PUT/DELETE /api/restaurants/{id}/pause

    /**
     * Pausing for 60 minutes at 12:00 blocks orders until 13:00. The restaurant stops accepting orders and is
     * not open, even though ALL_DAY would allow it, and the reason is stored trimmed.
     */
    @Test
    void pauseStopsOrdersUntilTheEndTime() throws Exception {
        long id = createRestaurant("Always Open", "ALL_DAY");

        putJson("/api/restaurants/" + id + "/pause", "{\"durationMinutes\": 60, \"reason\": \"  Kitchen overloaded  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.openNow").value(false))
                .andExpect(jsonPath("$.pausedUntil").value(startsWith("2026-10-26T13:00")))
                .andExpect(jsonPath("$.pauseReason").value("Kitchen overloaded"));
    }

    /**
     * Pausing again is safe: it only moves the end time (here to 14:00), it never switches the pause off.
     * A repeat without a reason clears the old reason.
     */
    @Test
    void pausingAgainMovesTheEndTime() throws Exception {
        long id = createRestaurant("Always Open", "ALL_DAY");
        putJson("/api/restaurants/" + id + "/pause", "{\"durationMinutes\": 60, \"reason\": \"Busy\"}");

        pause(id, 120)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.pausedUntil").value(startsWith("2026-10-26T14:00")))
                .andExpect(jsonPath("$.pauseReason").doesNotExist());
    }

    /** A pause must last between 1 minute and 24 hours, and the duration is required. */
    @Test
    void pauseRejectsInvalidDuration() throws Exception {
        long id = createRestaurant("Always Open", "ALL_DAY");

        pause(id, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").value("durationMinutes must be at least 1"));
        pause(id, 1441)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").value("durationMinutes must be at most 1440 (24 hours)"));
        putJson("/api/restaurants/" + id + "/pause", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").value("durationMinutes is required"));
    }

    /**
     * Resuming ends the pause early: the restaurant accepts orders again and the pause fields disappear.
     * Resuming a restaurant that is not paused is harmless and still returns 200.
     */
    @Test
    void resumeEndsThePauseAndIsSafeToRepeat() throws Exception {
        long id = createRestaurant("Always Open", "ALL_DAY");
        pause(id, 60);

        mockMvc.perform(delete("/api/restaurants/" + id + "/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.openNow").value(true))
                .andExpect(jsonPath("$.pausedUntil").doesNotExist())
                .andExpect(jsonPath("$.pauseReason").doesNotExist());

        mockMvc.perform(delete("/api/restaurants/" + id + "/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptingOrders").value(true));
    }

    /** Pausing or resuming a restaurant that does not exist returns 404. */
    @Test
    void pauseAndResumeUnknownRestaurant() throws Exception {
        pause(UNKNOWN_ID, 30).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/restaurants/" + UNKNOWN_ID + "/pause")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- POST/GET /api/restaurants/{id}/menu

    /** A new menu item is available, belongs to the restaurant, and its price is stored with 2 decimals. */
    @Test
    void addMenuItem() throws Exception {
        long restaurantId = createRestaurant("Jay Fai", null);

        postJson("/api/restaurants/" + restaurantId + "/menu", "{\"name\": \"  Tom Yum  \", \"price\": 250}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.name").value("Tom Yum"))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(content().string(containsString("\"price\":250.00")));
    }

    /**
     * Prices must be positive and have at most 2 decimal places (money cannot have fractions of a satang),
     * and the name is required.
     */
    @Test
    void addMenuItemRejectsInvalidInput() throws Exception {
        long restaurantId = createRestaurant("Jay Fai", null);
        String url = "/api/restaurants/" + restaurantId + "/menu";

        postJson(url, "{\"name\": \"Free Soup\", \"price\": 0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("price must be positive"));
        postJson(url, "{\"name\": \"Odd Soup\", \"price\": 10.555}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price", containsString("2 decimal places")));
        postJson(url, "{\"name\": \"\", \"price\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("name is required"));
    }

    /** A menu item cannot be added to a restaurant that does not exist. */
    @Test
    void addMenuItemToUnknownRestaurant() throws Exception {
        postJson("/api/restaurants/" + UNKNOWN_ID + "/menu", "{\"name\": \"Soup\", \"price\": 10}")
                .andExpect(status().isNotFound());
    }

    /**
     * The menu lists every item in the order they were added, including unavailable ones, so the restaurant
     * can see and re-enable sold-out dishes.
     */
    @Test
    void getMenuIncludesUnavailableItems() throws Exception {
        long restaurantId = createRestaurant("Jay Fai", null);
        addMenuItem(restaurantId, "Crab Omelette", "1000");
        long soldOut = addMenuItem(restaurantId, "Tom Yum", "250");
        mockMvc.perform(patch("/api/menu/" + soldOut + "/availability"));

        mockMvc.perform(get("/api/restaurants/" + restaurantId + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Crab Omelette", "Tom Yum")))
                .andExpect(jsonPath("$[*].isAvailable", contains(true, false)));
    }

    /**
     * An existing restaurant with no dishes returns an empty menu, while an unknown restaurant returns 404,
     * so the two cases are never confused.
     */
    @Test
    void getMenuOfEmptyAndUnknownRestaurant() throws Exception {
        long restaurantId = createRestaurant("New Place", null);

        mockMvc.perform(get("/api/restaurants/" + restaurantId + "/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/restaurants/" + UNKNOWN_ID + "/menu"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- PATCH /api/menu/{id}/availability

    /** Each call flips the dish between available and unavailable (for example sold out, then restocked). */
    @Test
    void toggleAvailabilityFlipsBackAndForth() throws Exception {
        long restaurantId = createRestaurant("Jay Fai", null);
        long itemId = addMenuItem(restaurantId, "Tom Yum", "250");

        mockMvc.perform(patch("/api/menu/" + itemId + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
        mockMvc.perform(patch("/api/menu/" + itemId + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    /** Toggling a menu item that does not exist returns 404. */
    @Test
    void toggleAvailabilityOfUnknownItem() throws Exception {
        mockMvc.perform(patch("/api/menu/" + UNKNOWN_ID + "/availability"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MenuItem with id " + UNKNOWN_ID + " not found"));
    }
}
