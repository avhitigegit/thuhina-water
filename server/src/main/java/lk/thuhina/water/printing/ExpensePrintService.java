package lk.thuhina.water.printing;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRow;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.expenses.service.ExpenseQueryService;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense statement for a month (design 10, prototype expenses.html "Print month"): every expense of the month by type and
 * date (paid on, type, description with the bill no., paid to, paid by, amount), the total, the totals by type, and
 * prepared / checked / approved signatures.
 */
@Service
public class ExpensePrintService {

    private static final boolean[] NUMERIC = {false, false, false, false, false, true};
    private static final float[] WIDTHS = {1.5f, 2.6f, 4f, 2.6f, 2.4f, 1.8f};
    private static final boolean[] TYPE_NUMERIC = {false, true};

    private final ExpenseQueryService expenses;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;
    private final Clock clock;

    public ExpensePrintService(ExpenseQueryService expenses, CompanyHeaderService company, PdfRenderer renderer, Clock clock) {
        this.expenses = expenses;
        this.company = company;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public byte[] month(String monthText) {
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.from(BusinessDates.today(clock))
                : ExpenseRules.parseMonth(monthText);
        if (month == null) {
            throw new ValidationException("month", ExpenseRules.MSG_MONTH);
        }
        String preparedBy = CurrentUser.get().map(AuthUser::fullName).orElse("");
        return renderer.render(company.current(), document(month, expenses.forPrint(month), preparedBy));
    }

    /** The statement as plain parts (also used by tests to check the wording). */
    public PrintDocument document(YearMonth month, List<ExpenseRow> rows, String preparedBy) {
        List<List<String>> lines = new ArrayList<>();
        Map<String, BigDecimal> byType = new LinkedHashMap<>();
        BigDecimal total = Money.ZERO;
        for (ExpenseRow e : rows) {
            String details = (e.description() == null ? "" : e.description()) + (e.billNo() == null ? "" : " (" + e.billNo() + ")");
            lines.add(List.of(BusinessDates.format(e.paidDate()), e.typeName(), details, e.paidTo() == null ? "" : e.paidTo(),
                    e.methodLabel() + (e.reference() == null ? "" : " " + e.reference()), BillPrintService.number(e.amount())));
            byType.merge(e.typeName(), e.amount(), BigDecimal::add);
            total = total.add(e.amount());
        }
        if (lines.isEmpty()) {
            lines.add(List.of("", "", "No expenses entered for this month", "", "", ""));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("Paid on", "Type", "Description", "Paid to", "Paid by",
                "Amount (Rs.)"), NUMERIC, lines, WIDTHS, 0);
        List<List<String>> typeRows = new ArrayList<>();
        byType.forEach((type, amount) -> typeRows.add(List.of(type, Money.format(amount))));
        List<PrintDocument.Table> tables = new ArrayList<>(List.of(table));
        if (!typeRows.isEmpty()) {
            tables.add(new PrintDocument.Table(List.of("By type", ""), TYPE_NUMERIC, typeRows));
        }
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Total", Money.format(total), true));
        return new PrintDocument("EXPENSES", List.of(ExpenseRules.monthLabel(month), "Printed " + BusinessDates.format(BusinessDates.today(clock))),
                List.of(), "Expenses for " + ExpenseRules.monthLabel(month) + " – counted in the month they are for", tables, summary,
                List.of(), List.of("Prepared by\n" + preparedBy, "Checked by", "Approved by"), PrintDocument.Page.A4_PORTRAIT);
    }
}
