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

/**
 * Tests the rule that decides whether a restaurant can take orders: it must not be paused, and the time must
 * be inside its opening-hours preset. Orders are refused with "Restaurant is closed" when this says no, and
 * the restaurant list is filtered by it, so a mistake here lets customers order from a closed kitchen.
 * Every test passes the moment to check explicitly, so the results never depend on the real clock.
 */
class OpeningHoursCheckerTest {

    // The clock is only needed to build the checker. The tests always give isOpenAt() an exact moment.
    private final OpeningHoursChecker checker = new OpeningHoursChecker(Clock.system(ZoneId.of("Asia/Bangkok")));

    // A fixed date to build moments from. 2026-10-26 is a Monday, so plusDays(n) walks through the week.
    private static LocalDateTime monday(int hour, int minute) {
        return LocalDateTime.of(2026, 10, 26, hour, minute);
    }

    // A restaurant with the given preset, not paused, so each test controls only what it is about.
    private static Restaurant restaurantWith(OpeningHours openingHours) {
        return Restaurant.builder().name("Test").openingHours(openingHours).build();
    }

    /**
     * A restaurant created without choosing opening hours gets ALL_DAY, meaning no time restriction. This keeps
     * a new restaurant usable straight away, and keeps restaurants created before opening hours existed open.
     */
    @Test
    void newRestaurantDefaultsToAllDay() {
        assertEquals(OpeningHours.ALL_DAY, Restaurant.builder().name("Test").build().getOpeningHours());
    }

    /**
     * ALL_DAY is open at every time of day: the first minute, the middle of the night and the last minute.
     * It is stored as 00:00 to 00:00, so this guards against that being read as "open for zero minutes".
     */
    @Test
    void allDayIsAlwaysOpen() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        assertTrue(checker.isOpenAt(r, monday(0, 0)));
        assertTrue(checker.isOpenAt(r, monday(3, 30)));
        assertTrue(checker.isOpenAt(r, monday(23, 59)));
    }

    /**
     * An ordinary window (OFFICE_HOURS, 09:00 to 21:00): open at noon, closed before opening and after
     * closing. This is the basic case: customers can order during the day and not outside it.
     */
    @Test
    void insideAndOutsideNormalWindow() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(checker.isOpenAt(r, monday(8, 59)));
        assertFalse(checker.isOpenAt(r, monday(22, 0)));
    }

    /**
     * The exact edges of a window. A restaurant that opens at 09:00 takes an order at 09:00, and one that
     * closes at 21:00 refuses an order at exactly 21:00. This pins down who wins at the boundary, so a
     * change from "before" to "before or equal" in the code is caught.
     */
    @Test
    void openTimeIsInclusiveAndCloseTimeIsExclusive() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        assertTrue(checker.isOpenAt(r, monday(9, 0)));
        assertFalse(checker.isOpenAt(r, monday(21, 0)));
    }

    /**
     * A preset applies to all seven days: at noon the restaurant is open every day from Monday to Sunday,
     * and at 22:00 it is closed every day. There are no per-weekday differences such as closing on Mondays.
     */
    @Test
    void sameScheduleAppliesEveryDayOfTheWeek() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        for (int i = 0; i < 7; i++) {
            assertTrue(checker.isOpenAt(r, monday(12, 0).plusDays(i)));
            assertFalse(checker.isOpenAt(r, monday(22, 0).plusDays(i)));
        }
    }

    /**
     * A window that crosses midnight (LATE_NIGHT, 20:00 to 04:00 the next morning). It is open in the evening
     * and in the early hours, closed in the daytime, and 04:00 itself is already closed. This is the case a
     * simple "open <= time < close" check gets wrong, because close (04:00) is earlier than open (20:00).
     */
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

    /**
     * Checks the times stored for MORNING (06:00 to 14:00) and EVENING (17:00 to 23:00). These are the times
     * the API returns and the README documents, so an accidental edit to a preset is caught here. The other
     * presets are covered by the behavior tests above.
     */
    @Test
    void everyPresetHasItsDocumentedWindow() {
        assertEquals(LocalTime.of(6, 0), OpeningHours.MORNING.getOpenTime());
        assertEquals(LocalTime.of(14, 0), OpeningHours.MORNING.getCloseTime());
        assertEquals(LocalTime.of(17, 0), OpeningHours.EVENING.getOpenTime());
        assertEquals(LocalTime.of(23, 0), OpeningHours.EVENING.getCloseTime());
    }

    /**
     * A pause closes a restaurant even in the middle of its opening hours. At noon an OFFICE_HOURS restaurant
     * would normally be open, but it is paused until 13:00 (for example "Kitchen overloaded"), so it is closed.
     */
    @Test
    void activePauseOverridesOpeningHours() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(13, 0), "Kitchen overloaded");
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    /**
     * A pause also works on a restaurant with no time restriction. ALL_DAY alone would always be open, so
     * this proves the pause is checked first and is the only thing that can close such a restaurant.
     */
    @Test
    void activePauseClosesAnAllDayRestaurant() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        r.pause(monday(13, 0), null);
        assertFalse(checker.isOpenAt(r, monday(12, 0)));
    }

    /**
     * A pause is a timestamp that expires on its own. Paused until 13:00: closed at 12:59, and open again
     * at exactly 13:00 and later, with nobody having to call "resume". This is the protection against an
     * owner forgetting a pause and a restaurant staying closed forever.
     */
    @Test
    void pauseEndsByItselfWithoutAnyCleanup() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(13, 0), null);
        assertFalse(checker.isOpenAt(r, monday(12, 59)));
        // The moment the pause end is reached the restaurant follows its opening hours again
        assertTrue(checker.isOpenAt(r, monday(13, 0)));
        assertTrue(checker.isOpenAt(r, monday(15, 0)));
    }

    /**
     * A pause can only close a restaurant, never open one. Paused until 10:00, at 22:00 the pause is over,
     * but the restaurant is still closed because 22:00 is outside OFFICE_HOURS. Ending a pause hands control
     * back to the opening hours, it does not force the restaurant open.
     */
    @Test
    void pauseDoesNotOpenARestaurantOutsideItsHours() {
        Restaurant r = restaurantWith(OpeningHours.OFFICE_HOURS);
        r.pause(monday(10, 0), null);
        assertFalse(checker.isOpenAt(r, monday(22, 0)));
    }

    /**
     * An owner can end a pause early. Paused until 13:00, then resumed: at 12:00 the restaurant is open again
     * and no longer counts as paused, even though the original end time had not been reached.
     */
    @Test
    void resumeEndsPauseEarly() {
        Restaurant r = restaurantWith(OpeningHours.ALL_DAY);
        r.pause(monday(13, 0), "Out of stock");
        r.resume();
        assertTrue(checker.isOpenAt(r, monday(12, 0)));
        assertFalse(r.isPausedAt(monday(12, 0)));
    }
}
