package orderpayment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PauseRestaurantRequest {

    // Capped at 24 hours: a longer pause must be renewed on purpose, so it cannot be forgotten
    @Schema(example = "60", description = "Pause orders for this many minutes (1-1440)")
    @NotNull(message = "durationMinutes is required")
    @Min(value = 1, message = "durationMinutes must be at least 1")
    @Max(value = 1440, message = "durationMinutes must be at most 1440 (24 hours)")
    private Integer durationMinutes;

    @Schema(example = "Kitchen is overloaded")
    @Size(max = 200, message = "reason must be at most 200 characters")
    private String reason;
}
