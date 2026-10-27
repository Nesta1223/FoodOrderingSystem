package com.pbgold.orderpayment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class RestaurantResponse {

    private Long id;
    private String name;
    private String category;
    // Manual switch set by the restaurant ("pause orders")
    private Boolean acceptingOrders;
    // Computed per request: acceptingOrders AND current time is inside the opening hours
    private Boolean openNow;
    private List<OpeningHoursResponse> openingHours;
    private LocalDateTime createdAt;
}
