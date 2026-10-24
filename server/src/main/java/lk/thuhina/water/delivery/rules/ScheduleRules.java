package lk.thuhina.water.delivery.rules;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Delivery schedule rules (design 5.4, BR-16, FR-46) – pure functions. A customer is delivered on one weekday every
 * {@code cycleWeeks} weeks; {@code next} is the next delivery date, which fixes the phase of the cycle.
 */
public final class ScheduleRules {

    private ScheduleRules() {
    }

    /** BR-16: the first delivery is the very next delivery day <b>after</b> the registration date. */
    public static LocalDate firstDelivery(LocalDate registeredOn, DayOfWeek day) {
        LocalDate d = registeredOn.plusDays(1);
        while (d.getDayOfWeek() != day) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** The first date on or after {@code from} that falls on {@code day}. */
    public static LocalDate onOrAfter(LocalDate from, DayOfWeek day) {
        LocalDate d = from;
        while (d.getDayOfWeek() != day) {
            d = d.plusDays(1);
        }
        return d;
    }

    /**
     * Due on {@code date}: active, on or after registration, on the delivery day, and a whole number of cycles away from
     * the next delivery date (before or after it).
     */
    public static boolean isDue(boolean active, LocalDate registeredOn, DayOfWeek day, int cycleWeeks, LocalDate next,
                                LocalDate date) {
        if (!active || date.isBefore(registeredOn) || date.getDayOfWeek() != day) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(next, date);
        return Math.floorMod(days, 7L * cycleWeeks) == 0;
    }

    /** The first date after {@code after} that is in step with the cycle of {@code next} (FR-58 "next"). */
    public static LocalDate nextAligned(LocalDate next, int cycleWeeks, LocalDate after) {
        long step = 7L * cycleWeeks;
        long days = ChronoUnit.DAYS.between(next, after);
        // Number of whole steps from next to the last aligned date on or before 'after', then one more.
        long k = Math.floorDiv(days, step) + 1;
        return next.plusDays(k * step);
    }

    /**
     * FR-46: after a delivery bill dated {@code txnDate}, move the next delivery date forward by whole cycles until it is
     * after the bill date. A bill before the next delivery date does not move it.
     */
    public static LocalDate advance(LocalDate next, int cycleWeeks, LocalDate txnDate) {
        LocalDate n = next;
        while (!n.isAfter(txnDate)) {
            n = n.plusWeeks(cycleWeeks);
        }
        return n;
    }

    /** Bottles a month at the usual quantity and cycle – a guide when agreeing a price (prototype Q.monthlyBottles). */
    public static int bottlesPerMonth(int usualQty, int cycleWeeks) {
        return (int) Math.round(usualQty * 52.0 / 12.0 / Math.max(1, cycleWeeks));
    }
}
