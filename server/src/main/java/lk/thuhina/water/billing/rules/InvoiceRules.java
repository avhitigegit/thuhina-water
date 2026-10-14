package lk.thuhina.water.billing.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.model.PayType;

/**
 * Monthly invoice rules (design 5.6, FR-49, prototype {@code Ops.invoicePreview} / {@code generateInvoices}): the months
 * that can be invoiced, the totals, who is skipped and the due date.
 */
public final class InvoiceRules {

    /** Months offered: the current one and the 3 before it. */
    public static final int MONTHS_BACK = 3;
    public static final int DEFAULT_TERMS = 30;

    public static final String SKIP_NOT_MONTHLY = "Not a Monthly bill customer";
    public static final String SKIP_EXISTS = "Invoice already exists";
    public static final String SKIP_NOTHING = "No deliveries and nothing due";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private InvoiceRules() {
    }

    /**
     * The totals of an invoice: {@code amount} = charges − paid on bills ("This month"); {@code totalDue} = opening + amount −
     * payments (the closing balance of the month).
     */
    public record InvoiceTotals(BigDecimal opening, BigDecimal charges, BigDecimal paidOnBills, BigDecimal payments, BigDecimal amount,
                                BigDecimal totalDue) {

        public static InvoiceTotals of(BigDecimal opening, BigDecimal charges, BigDecimal paidOnBills, BigDecimal payments) {
            BigDecimal amount = Money.of(charges).subtract(Money.of(paidOnBills));
            return new InvoiceTotals(Money.of(opening), Money.of(charges), Money.of(paidOnBills), Money.of(payments), amount,
                    Money.of(opening).add(amount).subtract(Money.of(payments)));
        }
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

    /** "September 2026" */
    public static String periodLabel(String period) {
        return MONTH.format(YearMonth.parse(period));
    }

    public static String period(LocalDate date) {
        return YearMonth.from(date).toString();
    }

    /** The months that can be invoiced, newest first: this month and the 3 before it. */
    public static List<String> periods(LocalDate today) {
        List<String> out = new ArrayList<>();
        YearMonth m = YearMonth.from(today);
        for (int i = 0; i <= MONTHS_BACK; i++) {
            out.add(m.minusMonths(i).toString());
        }
        return out;
    }

    /** Last month – the month normally invoiced at the start of a month. */
    public static String defaultPeriod(LocalDate today) {
        return YearMonth.from(today).minusMonths(1).toString();
    }

    /** Null when the month can be invoiced; otherwise the message. */
    public static String periodProblem(String period, LocalDate today) {
        if (!isPeriod(period)) {
            return "Choose the month.";
        }
        if (!periods(today).contains(period)) {
            return "Choose this month or one of the " + MONTHS_BACK + " months before it.";
        }
        return null;
    }

    /** Why a customer gets no invoice for the month; null = gets one. */
    public static String skipReason(PayType payType, boolean exists, int deliveries, BigDecimal totalDue) {
        if (payType != PayType.MONTHLY_BILL) {
            return SKIP_NOT_MONTHLY;
        }
        if (exists) {
            return SKIP_EXISTS;
        }
        if (deliveries == 0 && totalDue.signum() <= 0) {
            return SKIP_NOTHING;
        }
        return null;
    }

    /** Due date = invoice date + the customer's payment terms (30 days when none are set). */
    public static LocalDate dueDate(LocalDate invoiceDate, int termsDays) {
        return invoiceDate.plusDays(termsDays > 0 ? termsDays : DEFAULT_TERMS);
    }
}
