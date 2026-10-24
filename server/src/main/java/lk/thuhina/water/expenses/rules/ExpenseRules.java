package lk.thuhina.water.expenses.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.sales.model.PayMethod;

/**
 * Expense rules (FR-62, FR-63, BR-19) with the prototype's messages (prototype store.js Ops.saveExpense): an expense counts in
 * the month it is for, not the month it was paid; the paid date is not after today; the month is not after this month; a
 * cheque or bank transfer needs its reference.
 */
public final class ExpenseRules {

    public static final String MSG_MONTH = "Select the month the expense is for.";
    public static final String MSG_MONTH_FUTURE = "The month cannot be after this month.";
    public static final String MSG_TYPE = "Select the expense type.";
    public static final String MSG_TYPE_INACTIVE = "That expense type is not active.";
    public static final String MSG_AMOUNT = "Enter the amount paid.";
    public static final String MSG_METHOD = "Select the payment method.";
    public static final String MSG_REFERENCE = "Enter the cheque number or bank reference.";
    public static final String MSG_REASON = "Enter the reason for deleting.";
    public static final String MSG_TYPE_NAME = "Enter the expense type name.";
    public static final String MSG_TYPE_EXISTS = "That expense type already exists.";
    public static final String MSG_RANGE = "The From month must be on or before the To month.";
    public static final String MSG_RANGE_LONG = "Choose at most 36 months.";

    /** Months offered for the for-month and the page: at least this month and the 11 before it. */
    public static final int MIN_MONTHS = 12;
    public static final int MAX_SUMMARY_MONTHS = 36;
    public static final int SUMMARY_MONTHS = 6;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private ExpenseRules() {
    }

    /** A field and its message. */
    public record Problem(String field, String message) {
    }

    /** What the add / edit pop-up sends, already trimmed. */
    public record Input(LocalDate paidDate, YearMonth forMonth, boolean typeChosen, BigDecimal amount, PayMethod method, String reference) {
    }

    /** The first problem with an expense, or null. */
    public static Problem problem(Input in, LocalDate today) {
        if (in.paidDate() == null) {
            return new Problem("paidDate", "Enter a valid date (DD/MM/YYYY).");
        }
        if (in.paidDate().isAfter(today)) {
            return new Problem("paidDate", "Date cannot be after today (" + BusinessDates.format(today) + ").");
        }
        if (in.forMonth() == null) {
            return new Problem("forMonth", MSG_MONTH);
        }
        if (in.forMonth().isAfter(YearMonth.from(today))) {
            return new Problem("forMonth", MSG_MONTH_FUTURE);
        }
        if (!in.typeChosen()) {
            return new Problem("typeId", MSG_TYPE);
        }
        if (in.amount() == null || in.amount().signum() <= 0) {
            return new Problem("amount", MSG_AMOUNT);
        }
        if (in.method() == null) {
            return new Problem("method", MSG_METHOD);
        }
        if (in.method() != PayMethod.CASH && (in.reference() == null || in.reference().isBlank())) {
            return new Problem("reference", MSG_REFERENCE);
        }
        return null;
    }

    /** "2026-09" (or a date "2026-09-14") → September 2026; null when it is not a month. */
    public static YearMonth parseMonth(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}(-\\d{2})?")) {
            return null;
        }
        try {
            return YearMonth.parse(text.substring(0, 7));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "September 2026" */
    public static String monthLabel(YearMonth month) {
        return MONTH.format(month);
    }

    /** True when the expense was paid in another month than the one it is for (shown as "paid Oct"). */
    public static boolean paidInOtherMonth(LocalDate paidDate, YearMonth forMonth) {
        return !YearMonth.from(paidDate).equals(forMonth);
    }

    /**
     * The months to choose from, newest first: this month back to the earlier of the first expense's month and 11 months ago
     * (so a bill paid this month can still be entered for an earlier month).
     */
    public static List<YearMonth> months(YearMonth earliest, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        YearMonth first = current.minusMonths(MIN_MONTHS - 1L);
        if (earliest != null && earliest.isBefore(first)) {
            first = earliest;
        }
        List<YearMonth> out = new ArrayList<>();
        for (YearMonth m = current; !m.isBefore(first); m = m.minusMonths(1)) {
            out.add(m);
        }
        return out;
    }

    /** The months from {@code from} to {@code to}, oldest first; null message when the range is fine. */
    public static String rangeProblem(YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            return MSG_RANGE;
        }
        if (from.plusMonths(MAX_SUMMARY_MONTHS).isBefore(to.plusMonths(1))) {
            return MSG_RANGE_LONG;
        }
        return null;
    }

    public static List<YearMonth> range(YearMonth from, YearMonth to) {
        List<YearMonth> out = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            out.add(m);
        }
        return out;
    }
}
