package orderpayment.service;

import orderpayment.model.Restaurant;
import orderpayment.model.enums.OpeningHours;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningHoursCheckerTest {

    private final OpeningHoursChecker checker = new OpeningHoursChecker(Clock.system(ZoneId.of("Asia/Bangkok")));

    // 2026-10-26 is a Monday
    private static LocalDateTime monday(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 26, hour, minute);
    }

    private static Restaurant restaurantWith(OpeningHours openingHours) {
        return Restaurant.builder().name("Test").openingHours(openingHours).build();
    }

    @Test
    void newRestaurantDefaultsToAllDay() {
        assertEquals(OpeningHours.ALL_DAY, Restaurant.builder().name("Test").build().getOpeningHours());
    }

    @Test
    void allDayIsAlwaysOpen() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        assertTrue(checker.isOpenAt(r, monday(0, 0)));
        assertTrue(checker.isOpenAt(r, monday(3, 30)));
        assertTrue(checker.isOpenAt(r, monday(23, 59)));
    }

    @Test
    void insideAndOutsideNormalWindow() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(checker.isOpenAt(r, monday(8, 59)));
        assertFalse(checker.isOpenAt(r, monday(22, 0)));
    }

    @Test
    void openTimeIsInclusiveAndCloseTimeIsExclusive() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        assertTrue(checker.isOpenAt(r, monday(9, 0)));
        assertFalse(checker.isOpenAt(r, monday(21, 0)));
    }

    @Test
    void sameScheduleAppliesEveryDayOfTheWeek() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        for (int i = 0; i < 7; i++) {
            assertTrue(checker.isOpenAt(r, monday(12, 0).plusDays(i)));
            assertFalse(checker.isOpenAt(r, monday(22, 0).plusDays(i)));
        }
    }

    @Test
    void lateNightWindowRunsPastMidnight() {
        Restaurant r = restaurantWith(OpeningHours.LATE_NIGHT);
        assertTrue(checker.isOpenAt(r, monday(20, 0)));
        assertTrue(checker.isOpenAt(r, monday(23, 59)));
        assertTrue(checker.isOpenAt(r, monday(0, 0)));
        assertTrue(checker.isOpenAt(r, monday(3, 59)));
        assertFalse(checker.isOpenAt(r, monday(4, 0)));
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(checker.isOpenAt(r, monday(19, 59)));
    }

    @Test
    void everyPresetHasItsDocumentedWindow() {
        assertEquals(LocalTime.of(6, 0), OpeningHours.MORNING.getOpenTime());
        assertEquals(LocalTime.of(14, 0), OpeningHours.MORNING.getCloseTime());
        assertEquals(LocalTime.of(17, 0), OpeningHours.EVENING.getOpenTime());
        assertEquals(LocalTime.of(23, 0), OpeningHours.EVENING.getCloseTime());
    }

    @Test
    void activePauseOverridesOpeningHours() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(13, 0), "Kitchen overloaded");
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    @Test
    void activePauseClosesAnAllDayRestaurant() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        r.pause(monday(13, 0), null);
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    @Test
    void pauseEndsByItselfWithoutAnyCleanup() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(13, 0), null);
        assertFalse(checker.isOpenAt(r, monday(12, 59)));
        // The moment the pause end is reached the restaurant follows its opening hours again
        assertTrue(checker.isOpenAt(r, monday(13, 0)));
        assertTrue(checker.isOpenAt(r, monday(15, 0)));
    }

    @Test
    void pauseDoesNotOpenARestaurantOutsideItsHours() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(10, 0), null);
        assertFalse(checker.isOpenAt(r, monday(22, 0)));
    }

    @Test
    void resumeEndsPauseEarly() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        r.pause(monday(13, 0), "Out of stock");
        r.resume();
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(r.isPausedAt(monday(12, 0)));
    }
}
