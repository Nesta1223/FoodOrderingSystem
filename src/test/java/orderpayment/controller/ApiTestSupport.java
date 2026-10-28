package orderpayment.controller;

import com.jayway.jsonpath.JsonPath;
import orderpayment.model.User;
import orderpayment.repository.MenuItemRepository;
import orderpayment.repository.OrderItemRepository;
import orderpayment.repository.OrderRepository;
import orderpayment.repository.PaymentRepository;
import orderpayment.repository.RestaurantRepository;
import orderpayment.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the API tests. Each test sends real HTTP requests through MockMvc, so a request goes
 * through the controller, validation, service, repository and database, exactly as in the running app.
 * <ul>
 *   <li>Database: in-memory H2 (see application-test.yml), cleared before every test, so tests never
 *       depend on each other. The two demo users from DataSeeder are kept.</li>
 *   <li>Clock: frozen at {@link #NOW}, so opening hours and pauses give the same result on every run.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiTestSupport.FixedClockConfig.class)
abstract class ApiTestSupport {

    static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    // Monday 2026-10-26 at 12:00 in Bangkok. At this moment ALL_DAY, MORNING and OFFICE_HOURS restaurants
    // are open, while EVENING and LATE_NIGHT restaurants are closed.
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    // An id no test ever creates, for "not found" cases
    static final long UNKNOWN_ID = 999_999L;

    @TestConfiguration
    static class FixedClockConfig {

        // Takes priority over the real system clock from ClockConfig
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        }
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected MenuItemRepository menuItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    // Deletes children before parents so foreign keys are never violated
    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAllInBatch();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        menuItemRepository.deleteAllInBatch();
        restaurantRepository.deleteAllInBatch();
    }

    // ---- demo users (created by DataSeeder) ----

    protected long somchai() {
        return userIdByEmail("somchai@example.com");
    }

    protected long malee() {
        return userIdByEmail("malee@example.com");
    }

    private long userIdByEmail(String email) {
        return userRepository.findAll().stream()
                .filter(user -> user.getEmail().equals(email))
                .map(User::getId)
                .findFirst()
                .orElseThrow();
    }

    // ---- raw requests ----

    protected ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String json) throws Exception {
        return mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions patchJson(String url, String json) throws Exception {
        return mockMvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // Reads one value from the response body, e.g. jsonAt(result, "$.id")
    protected static Object jsonAt(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    protected static long idOf(ResultActions result) throws Exception {
        return ((Number) jsonAt(result, "$.id")).longValue();
    }

    // ---- business setup steps, each asserting it succeeded ----

    /** Creates a FOOD restaurant. openingHours may be null for the default (ALL_DAY). */
    protected long createRestaurant(String name, String openingHours) throws Exception {
        String hours = openingHours == null ? "" : ", \"openingHours\": \"" + openingHours + "\"";
        String json = "{\"name\": \"" + name + "\", \"category\": \"FOOD\"" + hours + "}";
        return idOf(postJson("/api/restaurants", json).andExpect(status().isCreated()));
    }

    protected long addMenuItem(long restaurantId, String name, String price) throws Exception {
        String json = "{\"name\": \"" + name + "\", \"price\": " + price + "}";
        return idOf(postJson("/api/restaurants/" + restaurantId + "/menu", json).andExpect(status().isCreated()));
    }

    protected ResultActions placeOrder(long userId, long restaurantId, long menuItemId, int quantity) throws Exception {
        String json = """
                {"userId": %d, "restaurantId": %d, "items": [{"menuItemId": %d, "quantity": %d}]}
                """.formatted(userId, restaurantId, menuItemId, quantity);
        return postJson("/api/orders", json);
    }

    protected long createOrder(long userId, long restaurantId, long menuItemId, int quantity) throws Exception {
        return idOf(placeOrder(userId, restaurantId, menuItemId, quantity).andExpect(status().isCreated()));
    }

    protected ResultActions pay(long orderId, String amount, String method) throws Exception {
        String json = "{\"orderId\": " + orderId + ", \"amount\": " + amount + ", \"method\": \"" + method + "\"}";
        return postJson("/api/payments", json);
    }

    protected ResultActions pause(long restaurantId, int minutes) throws Exception {
        return putJson("/api/restaurants/" + restaurantId + "/pause", "{\"durationMinutes\": " + minutes + "}");
    }
}
