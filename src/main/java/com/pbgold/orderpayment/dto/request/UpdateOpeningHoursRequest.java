package com.pbgold.orderpayment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOpeningHoursRequest {

    // An empty list is allowed: it clears the schedule, so the restaurant has no time restriction
    @NotNull(message = "openingHours is required")
    private List<@Valid @NotNull OpeningHoursRequest> openingHours;
}
