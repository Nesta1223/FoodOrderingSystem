package com.pbgold.orderpayment.service;

import com.pbgold.orderpayment.model.OpeningHours;
import com.pbgold.orderpayment.model.Restaurant;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningHoursCheckerTest {

    private final OpeningHoursChecker checker = new OpeningHoursChecker(Clock.system(ZoneId.of("Asia/Bangkok")));

    // 2026-10-26 is a Monday
    private static LocalDateTime monday(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 26, hour, minute);
    }

    private static Restaurant restaurantWith(OpeningHours... periods) {
        Restaurant restaurant = Restaurant.builder().name("Test").build();
        for (OpeningHours p : periods) {
            p.setRestaurant(restaurant);
            restaurant.getOpeningHours().add(p);
        }
        return restaurant;
    }

    private static OpeningHours period(DayOfWeek day, String open, String close) {
        return OpeningHours.builder()
                .dayOfWeek(day)
                .openTime(LocalTime.parse(open))
                .closeTime(LocalTime.parse(close))
                .build();
    }

    @Test
    void noScheduleMeansAlwaysOpen() {
        assertTrue(checker.isOpenAt(restaurantWith(), monday(3, 0)));
    }

    @Test
    void manualSwitchOffOverridesSchedule() {
        Restaurant r = restaurantWith(period(DayOfWeek.MONDAY, "09:00", "21:00"));
        r.setIsOpen(false);
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    @Test
    void insideAndOutsideNormalPeriod() {
        Restaurant r = restaurantWith(period(DayOfWeek.MONDAY, "09:00", "21:00"));
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(checker.isOpenAt(r, monday(8, 59)));
        assertFalse(checker.isOpenAt(r, monday(22, 0)));
    }

    @Test
    void openTimeIsInclusiveAndCloseTimeIsExclusive() {
        Restaurant r = restaurantWith(period(DayOfWeek.MONDAY, "09:00", "21:00"));
        assertTrue(checker.isOpenAt(r, monday(9, 0)));
        assertFalse(checker.isOpenAt(r, monday(21, 0)));
    }

    @Test
    void closedOnDaysWithoutAPeriod() {
        Restaurant r = restaurantWith(period(DayOfWeek.TUESDAY, "09:00", "21:00"));
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    @Test
    void twoPeriodsInOneDay() {
        Restaurant r = restaurantWith(
                period(DayOfWeek.MONDAY, "11:00", "14:00"),
                period(DayOfWeek.MONDAY, "17:00", "21:00"));
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(checker.isOpenAt(r, monday(15, 0)));
        assertTrue(checker.isOpenAt(r, monday(18, 0)));
    }

    @Test
    void overnightPeriodCoversLateEveningAndNextMorning() {
        Restaurant r = restaurantWith(period(DayOfWeek.MONDAY, "18:00", "02:00"));
        assertTrue(checker.isOpenAt(r, monday(23, 0)));
        assertFalse(checker.isOpenAt(r, monday(17, 59)));
        // Tuesday 01:00 is still covered by Monday's period
        assertTrue(checker.isOpenAt(r, LocalDateTime.of(2026, 10, 27, 1, 0)));
        assertFalse(checker.isOpenAt(r, LocalDateTime.of(2026, 10, 27, 2, 0)));
        // ... but Monday 01:00 is not (that would be Sunday's period)
        assertFalse(checker.isOpenAt(r, monday(1, 0)));
    }

    @Test
    void overnightPeriodWrapsFromSundayToMonday() {
        Restaurant r = restaurantWith(period(DayOfWeek.SUNDAY, "22:00", "03:00"));
        assertTrue(checker.isOpenAt(r, monday(1, 0)));
    }

    @Test
    void closingAtMidnightDoesNotLeakIntoNextDay() {
        Restaurant r = restaurantWith(period(DayOfWeek.MONDAY, "09:00", "00:00"));
        assertTrue(checker.isOpenAt(r, monday(23, 59)));
        assertFalse(checker.isOpenAt(r, LocalDateTime.of(2026, 10, 27, 0, 0)));
    }
}
