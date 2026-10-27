package com.pbgold.orderpayment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpeningHoursRequest {

    @Schema(example = "MONDAY", allowableValues = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"})
    @NotBlank(message = "dayOfWeek is required")
    private String dayOfWeek;

    @Schema(type = "string", example = "09:00")
    @NotNull(message = "openTime is required")
    private LocalTime openTime;

    @Schema(type = "string", example = "21:00", description = "If not after openTime, the period runs past midnight")
    @NotNull(message = "closeTime is required")
    private LocalTime closeTime;
}
