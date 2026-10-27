package com.pbgold.orderpayment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
@AllArgsConstructor
public class OpeningHoursResponse {

    private String dayOfWeek;

    @Schema(type = "string", example = "09:00")
    private LocalTime openTime;

    @Schema(type = "string", example = "21:00")
    private LocalTime closeTime;
}
