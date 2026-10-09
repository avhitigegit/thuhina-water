package lk.thuhina.water.numbering.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import lk.thuhina.water.numbering.model.ResetRule;

/**
 * Pure formatting rules for document numbers (design 5.3):
 * prefix + (period + "-" for yearly / monthly sequences) + zero-padded value.
 * <ul>
 *   <li>NONE, prefix "C", padding 4, value 7 → {@code C0007}</li>
 *   <li>YEARLY, prefix "PO-", padding 4, 2026 → {@code PO-2026-0001}</li>
 *   <li>MONTHLY, prefix "INV-", padding 4, October 2026 → {@code INV-2610-0001}</li>
 * </ul>
 */
public final class SequenceFormat {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyMM");

    private SequenceFormat() {
    }

    /** Period stored in {@code doc_sequence.period_key}: {@code 2026} (yearly), {@code 2610} (monthly), "" (never). */
    public static String periodKey(ResetRule rule, LocalDate date) {
        return switch (rule) {
            case NONE -> "";
            case YEARLY -> YEAR.format(date);
            case MONTHLY -> YEAR_MONTH.format(date);
        };
    }

    public static String format(String prefix, ResetRule rule, String periodKey, long value, int padding) {
        String number = String.format("%0" + padding + "d", value);
        return rule == ResetRule.NONE ? prefix + number : prefix + periodKey + "-" + number;
    }
}
