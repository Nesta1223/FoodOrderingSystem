package com.pbgold.orderpayment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, LocalDateTime.now().withNano(0), null);
    }
}
