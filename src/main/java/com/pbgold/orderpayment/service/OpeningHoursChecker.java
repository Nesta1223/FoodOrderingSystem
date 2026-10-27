package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.model.OpeningHours;
import com.pbgold.orderpayment.model.Restaurant;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Decides whether a restaurant is open: manual switch on AND current time inside one of its periods.
 */
@Component
public class OpeningHoursChecker {

    private final Clock clock;

    public OpeningHoursChecker(Clock clock) {
        this.clock = clock;
    }

    public boolean isOpenNow(Restaurant restaurant) {
        return isOpenAt(restaurant, LocalDateTime.now(clock));
    }

    public boolean isOpenAt(Restaurant restaurant, LocalDateTime moment) {
        if (!Boolean.TRUE.equals(restaurant.getIsOpen())) {
            return false;
        }
        if (restaurant.getOpeningHours().isEmpty()) {
            return true;
        }
        DayOfWeek today = moment.getDayOfWeek();
        LocalTime time = moment.toLocalTime();
        return restaurant.getOpeningHours().stream().anyMatch(period -> covers(period, today, time));
    }

    private boolean covers(OpeningHours period, DayOfWeek today, LocalTime time) {
        if (period.getDayOfWeek() == today) {
            if (period.isOvernight()) {
                return !time.isBefore(period.getOpenTime());
            }
            return !time.isBefore(period.getOpenTime()) && time.isBefore(period.getCloseTime());
        }
        // The tail of yesterday's overnight period (e.g. Mon 18:00-02:00 still covers Tue 01:00)
        return period.isOvernight()
                && period.getDayOfWeek() == today.minus(1)
                && time.isBefore(period.getCloseTime());
    }
}
