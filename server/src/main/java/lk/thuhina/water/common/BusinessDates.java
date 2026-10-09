package lk.thuhina.water.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Business date helpers. The business runs in Asia/Colombo; "today" always comes from the
 * injected {@link Clock} so tests can fix it. The API uses ISO dates; DD/MM/YYYY is for prints.
 */
public final class BusinessDates {

    public static final String ZONE_ID = "Asia/Colombo";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);
    public static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BusinessDates() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** {@code 05/10/2026}; empty text for null. */
    public static String format(LocalDate date) {
        return date == null ? "" : DISPLAY.format(date);
    }

    public static LocalDate firstOfMonth(LocalDate date) {
        return date.withDayOfMonth(1);
    }
}
