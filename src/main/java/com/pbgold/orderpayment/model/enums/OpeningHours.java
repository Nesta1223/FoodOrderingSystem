package com.pbgold.orderpayment.model.enums;

import lombok.Getter;

import java.time.LocalTime;

/**
 * Preset daily opening schedules. A restaurant picks one and it applies to every day of the week.
 * If closeTime is not after openTime the window runs past midnight into the next day.
 */
@Getter
public enum OpeningHours {

    // 00:00-00:00 counts as the whole day: the "past midnight" rule below makes every time match
    ALL_DAY(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),
    MORNING(LocalTime.of(6, 0), LocalTime.of(14, 0)),
    OFFICE_HOURS(LocalTime.of(9, 0), LocalTime.of(21, 0)),
    EVENING(LocalTime.of(17, 0), LocalTime.of(23, 0)),
    LATE_NIGHT(LocalTime.of(20, 0), LocalTime.of(4, 0));

    private final LocalTime openTime;
    private final LocalTime closeTime;

    OpeningHours(LocalTime openTime, LocalTime closeTime) {
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    // openTime is inclusive, closeTime is exclusive
    public boolean isOpenAt(LocalTime time) {
        if (!closeTime.isAfter(openTime)) {
            return !time.isBefore(openTime) || time.isBefore(closeTime);
        }
        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }
}
