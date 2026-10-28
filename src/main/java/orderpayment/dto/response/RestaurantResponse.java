package orderpayment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class RestaurantResponse {

    private Long id;
    private String name;
    private String category;
    // False while a manual pause is active
    private Boolean acceptingOrders;
    // Computed per request: not paused AND the current time is inside the opening hours
    private Boolean openNow;
    // Only present while a pause is active
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDateTime pausedUntil;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String pauseReason;
    private OpeningHoursResponse openingHours;
    private LocalDateTime createdAt;
}
