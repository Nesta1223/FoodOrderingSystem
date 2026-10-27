package com.pbgold.orderpayment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderStatusRequest {

    @Schema(example = "CONFIRMED", allowableValues = {"PENDING", "CONFIRMED", "PREPARING", "DELIVERED", "CANCELLED"})
    @NotBlank(message = "status is required")
    private String status;
}
