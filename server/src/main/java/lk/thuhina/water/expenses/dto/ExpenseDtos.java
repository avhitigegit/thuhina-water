package lk.thuhina.water.expenses.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.PayMethod;

/** Expenses (design 6 Expenses, 7.2): expense types, expenses by the month they are for, month-by-month summary. */
public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    // ------------------------------------------------------------------ types (FR-63)

    public record ExpenseTypeView(long id, String name, @Schema(nullable = true) String description, boolean active, long version) {
    }

    /** New or changed type; {@code active} default true for a new type, unchanged on an edit. */
    public record ExpenseTypeRequest(
            @Size(max = 60, message = "Name: at most 60 characters.") String name,
            @Size(max = 200, message = "Description: at most 200 characters.") String description,
            Boolean active,
            Long version) {
    }

    // ------------------------------------------------------------------ expenses (FR-62)

    /**
     * An expense. {@code forMonth} = the month it counts in ("2026-09"); {@code paidInOtherMonth} = paid in another month
     * (September electricity paid on 02/10).
     */
    public record ExpenseRow(
            long id,
            String expenseNo,
            LocalDate paidDate,
            String forMonth,
            String forMonthLabel,
            boolean paidInOtherMonth,
            long typeId,
            String typeName,
            @Schema(nullable = true) String description,
            @Schema(nullable = true) String paidTo,
            @Schema(nullable = true) String billNo,
            BigDecimal amount,
            String method,
            String methodLabel,
            @Schema(nullable = true) String reference,
            String createdByName,
            long version) {
    }

    /** Add / edit. {@code forMonth} "2026-09"; reference required unless Cash; {@code version} on an edit. */
    public record ExpenseRequest(
            LocalDate paidDate,
            String forMonth,
            Long typeId,
            @Size(max = 200, message = "Description: at most 200 characters.") String description,
            @Size(max = 100, message = "Paid to: at most 100 characters.") String paidTo,
            @Size(max = 50, message = "Bill / account no.: at most 50 characters.") String billNo,
            BigDecimal amount,
            PayMethod method,
            @Size(max = 100, message = "Reference: at most 100 characters.") String reference,
            Long version) {
    }

    /** After saving: the expense and "EX-0021 saved – Rs. 4,260.00 for September 2026" (or "Expense updated"). */
    public record ExpenseSaved(ExpenseRow expense, String message) {
    }

    public record DeleteExpenseRequest(@Size(max = 200, message = "Reason: at most 200 characters.") String reason, Long version) {
    }

    public record MonthOption(String month, String label) {
    }

    public record ExpenseTypeTotal(long typeId, String name, BigDecimal total) {
    }

    /**
     * The Monthly expenses tab for one month: the entries that match the type / search (with their total), and for the whole
     * month the total, the entries, the previous month and the difference, and the totals by type (biggest first).
     * {@code payees} = earlier "paid to" names for the pop-up's suggestions.
     */
    public record ExpenseMonthView(
            String month,
            String monthLabel,
            List<MonthOption> months,
            List<ExpenseRow> items,
            BigDecimal shownTotal,
            BigDecimal total,
            int entries,
            String previousMonth,
            String previousLabel,
            BigDecimal previousTotal,
            BigDecimal difference,
            List<ExpenseTypeTotal> byType,
            List<String> payees) {
    }

    // ------------------------------------------------------------------ summary (month by month; Profit and Expenses reports)

    public record ExpenseSummaryMonth(String month, String label, BigDecimal total) {
    }

    /** One type: the amount for each month of the summary (same order as its months) and the total. */
    public record ExpenseSummaryRow(long typeId, String name, boolean active, List<BigDecimal> amounts, BigDecimal total) {
    }

    /** Type × month from {@code from} to {@code to} (oldest first): active types and any type with an amount in the range. */
    public record ExpenseSummary(String from, String to, List<ExpenseSummaryMonth> months, List<ExpenseSummaryRow> rows,
                                 BigDecimal total) {
    }
}
