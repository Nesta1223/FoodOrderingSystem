package orderpayment.service;

import orderpayment.model.Restaurant;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Decides whether a restaurant is open: not paused AND the current time is inside its opening-hours preset.
 */
@Component
public class OpeningHoursChecker {

    private final Clock clock;

    public OpeningHoursChecker(Clock clock) {
        this.clock = clock;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public boolean isOpenNow(Restaurant restaurant) {
        return isOpenAt(restaurant, now());
    }

    public boolean isOpenAt(Restaurant restaurant, LocalDateTime moment) {
        return !restaurant.isPausedAt(moment) && restaurant.getOpeningHours().isOpenAt(moment.toLocalTime());
    }
}
