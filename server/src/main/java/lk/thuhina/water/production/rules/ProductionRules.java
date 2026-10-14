package lk.thuhina.water.production.rules;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import lk.thuhina.water.common.Money;

/**
 * Filling factory rules without the database (FR-24 – FR-26, BR-11): batch status, return checks, filling cost and the
 * monthly payment status.
 */
public final class ProductionRules {

    public static final String AT_FACTORY = "AT_FACTORY";
    public static final String PARTLY_RETURNED = "PARTLY_RETURNED";
    public static final String RETURNED = "RETURNED";

    public static final String PAID = "PAID";
    public static final String PART_PAID = "PART_PAID";
    public static final String UNPAID = "UNPAID";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private ProductionRules() {
    }

    /** Bottles of a batch still at the factory = sent − filled back − rejected. */
    public static int stillAtFactory(int sent, int filled, int rejected) {
        return sent - filled - rejected;
    }

    /** At factory (nothing back yet), Partly returned, Returned (nothing left at the factory). */
    public static String batchStatus(int sent, int filled, int rejected) {
        int left = stillAtFactory(sent, filled, rejected);
        return left == 0 ? RETURNED : filled + rejected > 0 ? PARTLY_RETURNED : AT_FACTORY;
    }

    public static String batchStatusLabel(String status) {
        return switch (status) {
            case RETURNED -> "Returned";
            case PARTLY_RETURNED -> "Partly returned";
            default -> "At factory";
        };
    }

    /** Checks a return; returns the message, or null when it is fine. */
    public static String returnProblem(int stillAtFactory, int filled, int rejected, String rejectReason) {
        if (filled < 0 || rejected < 0) {
            return "Quantities cannot be negative.";
        }
        if (filled + rejected < 1) {
            return "Enter filled and/or rejected bottles received.";
        }
        if (filled + rejected > stillAtFactory) {
            return "Only " + stillAtFactory + " bottles of this batch are still at the factory.";
        }
        if (rejected > 0 && (rejectReason == null || rejectReason.isBlank())) {
            return "Enter the reason for the rejected bottles.";
        }
        return null;
    }

    /** Filling cost = bottles filled × the batch's charge (BR-11) – rejected bottles are not charged. */
    public static BigDecimal cost(int filled, BigDecimal charge) {
        return Money.times(charge, filled);
    }

    /** Paid when nothing is due (1 cent tolerance), Part paid when something was paid, else Unpaid. */
    public static String paymentStatus(BigDecimal cost, BigDecimal paid) {
        if (cost.subtract(paid).compareTo(new BigDecimal("0.01")) <= 0) {
            return PAID;
        }
        return paid.signum() > 0 ? PART_PAID : UNPAID;
    }

    public static String paymentStatusLabel(String status) {
        return switch (status) {
            case PAID -> "Paid";
            case PART_PAID -> "Part paid";
            default -> "Unpaid";
        };
    }

    /** "2026-08" → "August 2026". */
    public static String periodLabel(String period) {
        return MONTH.format(YearMonth.parse(period));
    }

    /** True for a valid YYYY-MM month. */
    public static boolean isPeriod(String period) {
        if (period == null || !period.matches("\\d{4}-\\d{2}")) {
            return false;
        }
        try {
            YearMonth.parse(period);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
