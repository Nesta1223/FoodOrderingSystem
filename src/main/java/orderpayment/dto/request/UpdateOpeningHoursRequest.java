package orderpayment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOpeningHoursRequest {

    @Schema(example = "OFFICE_HOURS", allowableValues = {"ALL_DAY", "MORNING", "OFFICE_HOURS", "EVENING", "LATE_NIGHT"})
    @NotBlank(message = "openingHours is required")
    private String openingHours;
}
