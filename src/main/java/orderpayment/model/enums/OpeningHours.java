package orderpayment.model.enums;

import lombok.Getter;

import java.time.LocalTime;

/**
 * Preset daily opening schedules. A restaurant picks one and it applies to every day of the week.
 * If closeTime is not after openTime the window runs past midnight into the next day.
 */
@Getter
public enum OpeningHours {

    /**
     * Open at any time of day, every day: no time restriction. This is the default for a new restaurant.
     * 00:00 to 00:00 counts as the whole day, because the "past midnight" rule below makes every time match.
     */
    ALL_DAY(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT),

    /** 06:00 to 14:00. Suits breakfast and lunch places. */
    MORNING(LocalTime.of(6, 0), LocalTime.of(14, 0)),

    /** 09:00 to 21:00. A typical daytime-and-evening shop. */
    OFFICE_HOURS(LocalTime.of(9, 0), LocalTime.of(21, 0)),

    /** 17:00 to 23:00. Suits dinner and evening places. */
    EVENING(LocalTime.of(17, 0), LocalTime.of(23, 0)),

    /** 20:00 to 04:00 the next morning. Suits night-time food. The window runs past midnight. */
    LATE_NIGHT(LocalTime.of(20, 0), LocalTime.of(4, 0));

    /**
     * The time of day from which orders are accepted. This moment itself is included.
     */
    private final LocalTime openTime;

    /**
     * The time of day at which orders stop being accepted. This moment itself is NOT included, so a
     * restaurant closing at 21:00 refuses an order at exactly 21:00. If it is not after openTime, the
     * window runs past midnight into the next morning.
     */
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
