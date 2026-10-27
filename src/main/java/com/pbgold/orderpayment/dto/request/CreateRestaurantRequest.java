package com.pbgold.orderpayment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRestaurantRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @Schema(example = "FOOD", allowableValues = {"FOOD", "DRINK", "DESSERT"})
    @NotBlank(message = "category is required")
    private String category;

    // Optional. Omitted or empty means no time restriction
    private List<@Valid @NotNull OpeningHoursRequest> openingHours;
}
