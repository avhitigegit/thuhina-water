package lk.thuhina.water.expenses.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseMonthView;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRow;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummary;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummaryMonth;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummaryRow;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeTotal;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseTypeView;
import lk.thuhina.water.expenses.dto.ExpenseDtos.MonthOption;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.sales.model.PayMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expense reads (design 6 Expenses): the month view (entries, total, previous month, by type), the type × month summary that
 * also feeds the Profit and Expenses reports (M12), and the types. Deleted expenses never count.
 */
@Service
@Transactional(readOnly = true)
public class ExpenseQueryService {

    private static final String ROW_SQL = "select e.id, e.expense_no, e.paid_date, e.for_month, e.expense_type_id, t.name as type_name, "
            + "e.description, e.paid_to, e.bill_no, e.amount, e.method, e.reference, e.created_by, e.version "
            + "from expense e join expense_type t on t.id = e.expense_type_id ";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ExpenseQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ types

    public List<ExpenseTypeView> types() {
        return jdbc.query("select id, name, description, active, version from expense_type order by id", Map.of(),
                (rs, i) -> new ExpenseTypeView(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("active"),
                        rs.getLong("version")));
    }

    // ------------------------------------------------------------------ one expense

    public ExpenseRow row(long id) {
        List<Raw> raw = jdbc.query(ROW_SQL + "where e.id = :id and not e.deleted", Map.of("id", id), ExpenseQueryService::raw);
        if (raw.isEmpty()) {
            throw new NotFoundException("Expense", id);
        }
        return rows(raw).getFirst();
    }

    // ------------------------------------------------------------------ month view

    /** The Monthly expenses tab; month default this month, type and search optional. */
    public ExpenseMonthView month(String monthText, Long typeId, String q) {
        LocalDate today = BusinessDates.today(clock);
        YearMonth month = monthText == null || monthText.isBlank() ? YearMonth.from(today) : ExpenseRules.parseMonth(monthText);
        if (month == null) {
            throw new ValidationException("month", ExpenseRules.MSG_MONTH);
        }
        YearMonth previous = month.minusMonths(1);
        List<ExpenseRow> all = rows(jdbc.query(ROW_SQL + "where not e.deleted and e.for_month = :m order by e.paid_date, e.id",
                Map.of("m", month.atDay(1)), ExpenseQueryService::raw));
        String search = Texts.blankToNull(q);
        List<ExpenseRow> shown = all.stream()
                .filter(e -> typeId == null || e.typeId() == typeId)
                .filter(e -> search == null || matches(e, search.toLowerCase()))
                .toList();

        Map<Long, ExpenseTypeTotal> byType = new LinkedHashMap<>();
        for (ExpenseRow e : all) {
            ExpenseTypeTotal t = byType.get(e.typeId());
            byType.put(e.typeId(), new ExpenseTypeTotal(e.typeId(), e.typeName(), (t == null ? Money.ZERO : t.total()).add(e.amount())));
        }
        List<ExpenseTypeTotal> types = byType.values().stream()
                .sorted(Comparator.comparing(ExpenseTypeTotal::total).reversed().thenComparing(ExpenseTypeTotal::name))
                .toList();
        BigDecimal total = sum(all);
        BigDecimal previousTotal = monthTotal(previous);
        List<MonthOption> months = months(today).stream().map(m -> new MonthOption(m.toString(), ExpenseRules.monthLabel(m))).toList();
        return new ExpenseMonthView(month.toString(), ExpenseRules.monthLabel(month), months, shown, sum(shown), total, all.size(),
                previous.toString(), ExpenseRules.monthLabel(previous), previousTotal, total.subtract(previousTotal), types, payees());
    }

    /** This month back to the earlier of the first expense's month and 11 months ago. */
    public List<YearMonth> months(LocalDate today) {
        LocalDate first = jdbc.queryForObject("select min(for_month) from expense where not deleted", Map.of(), LocalDate.class);
        return ExpenseRules.months(first == null ? null : YearMonth.from(first), today);
    }

    /** Σ expenses for a month (0.00 when none). */
    public BigDecimal monthTotal(YearMonth month) {
        BigDecimal v = jdbc.queryForObject("select coalesce(sum(amount), 0) from expense where not deleted and for_month = :m",
                Map.of("m", month.atDay(1)), BigDecimal.class);
        return Money.of(v);
    }

    // ------------------------------------------------------------------ summary

    /**
     * Type × month from {@code from} to {@code to} ("2026-05" or a date in that month). Default: the 6 months up to this month.
     * Rows: active types and any type with an amount in the range, in the types' order.
     */
    public ExpenseSummary summary(String fromText, String toText) {
        YearMonth current = YearMonth.from(BusinessDates.today(clock));
        YearMonth to = toText == null || toText.isBlank() ? current : ExpenseRules.parseMonth(toText);
        if (to == null) {
            throw new ValidationException("to", ExpenseRules.MSG_MONTH);
        }
        YearMonth from = fromText == null || fromText.isBlank() ? to.minusMonths(ExpenseRules.SUMMARY_MONTHS - 1L)
                : ExpenseRules.parseMonth(fromText);
        if (from == null) {
            throw new ValidationException("from", ExpenseRules.MSG_MONTH);
        }
        String problem = ExpenseRules.rangeProblem(from, to);
        if (problem != null) {
            throw new ValidationException("from", problem);
        }
        List<YearMonth> months = ExpenseRules.range(from, to);
        Map<Long, Map<YearMonth, BigDecimal>> amounts = new HashMap<>();
        jdbc.query("select expense_type_id, for_month, sum(amount) as total from expense where not deleted "
                        + "and for_month between :f and :t group by expense_type_id, for_month",
                Map.of("f", from.atDay(1), "t", to.atDay(1)), rs -> {
                    amounts.computeIfAbsent(rs.getLong("expense_type_id"), k -> new HashMap<>())
                            .put(YearMonth.from(rs.getObject("for_month", LocalDate.class)), Money.of(rs.getBigDecimal("total")));
                });
        List<ExpenseSummaryRow> rows = new ArrayList<>();
        for (ExpenseTypeView t : types()) {
            Map<YearMonth, BigDecimal> byMonth = amounts.getOrDefault(t.id(), Map.of());
            if (!t.active() && byMonth.isEmpty()) {
                continue;
            }
            List<BigDecimal> values = months.stream().map(m -> byMonth.getOrDefault(m, Money.ZERO)).toList();
            rows.add(new ExpenseSummaryRow(t.id(), t.name(), t.active(), values, values.stream().reduce(Money.ZERO, BigDecimal::add)));
        }
        List<ExpenseSummaryMonth> monthTotals = new ArrayList<>();
        for (int i = 0; i < months.size(); i++) {
            int col = i;
            monthTotals.add(new ExpenseSummaryMonth(months.get(i).toString(), ExpenseRules.monthLabel(months.get(i)),
                    rows.stream().map(r -> r.amounts().get(col)).reduce(Money.ZERO, BigDecimal::add)));
        }
        return new ExpenseSummary(from.toString(), to.toString(), monthTotals, rows,
                rows.stream().map(ExpenseSummaryRow::total).reduce(Money.ZERO, BigDecimal::add));
    }

    /** Every expense of a month for the printed statement, by type then date. */
    public List<ExpenseRow> forPrint(YearMonth month) {
        return rows(jdbc.query(ROW_SQL + "where not e.deleted and e.for_month = :m order by t.name, e.paid_date, e.id",
                Map.of("m", month.atDay(1)), ExpenseQueryService::raw));
    }

    // ------------------------------------------------------------------ helpers

    private List<String> payees() {
        return jdbc.queryForList("select paid_to from expense where not deleted and paid_to is not null "
                + "group by paid_to order by max(paid_date) desc limit 50", Map.of(), String.class);
    }

    private static boolean matches(ExpenseRow e, String q) {
        String text = String.join(" ", e.expenseNo(), nz(e.description()), nz(e.paidTo()), nz(e.billNo()), nz(e.reference()),
                e.typeName()).toLowerCase();
        return text.contains(q);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static BigDecimal sum(Collection<ExpenseRow> rows) {
        return rows.stream().map(ExpenseRow::amount).reduce(Money.ZERO, BigDecimal::add);
    }

    private record Raw(long id, String expenseNo, LocalDate paidDate, LocalDate forMonth, long typeId, String typeName, String description,
                       String paidTo, String billNo, BigDecimal amount, String method, String reference, String createdBy, long version) {
    }

    private static Raw raw(ResultSet rs, int i) throws SQLException {
        return new Raw(rs.getLong("id"), rs.getString("expense_no"), rs.getObject("paid_date", LocalDate.class),
                rs.getObject("for_month", LocalDate.class), rs.getLong("expense_type_id"), rs.getString("type_name"),
                rs.getString("description"), rs.getString("paid_to"), rs.getString("bill_no"), Money.of(rs.getBigDecimal("amount")),
                rs.getString("method"), rs.getString("reference"), rs.getString("created_by"), rs.getLong("version"));
    }

    private List<ExpenseRow> rows(List<Raw> raw) {
        Map<String, String> names = userNames(raw.stream().map(Raw::createdBy).toList());
        return raw.stream().map(r -> {
            YearMonth m = YearMonth.from(r.forMonth());
            PayMethod method = PayMethod.valueOf(r.method());
            return new ExpenseRow(r.id(), r.expenseNo(), r.paidDate(), m.toString(), ExpenseRules.monthLabel(m),
                    ExpenseRules.paidInOtherMonth(r.paidDate(), m), r.typeId(), r.typeName(), r.description(), r.paidTo(), r.billNo(),
                    r.amount(), method.name(), method.label(), r.reference(), names.get(r.createdBy()), r.version());
        }).toList();
    }

    /** Full names of users; usernames without a user (seed, system) are shown as they are. */
    private Map<String, String> userNames(Collection<String> usernames) {
        Set<String> wanted = new HashSet<>(usernames);
        wanted.remove(null);
        Map<String, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted),
                    rs -> {
                        out.put(rs.getString("username"), rs.getString("full_name"));
                    });
        }
        wanted.forEach(u -> out.putIfAbsent(u, u));
        return out;
    }
}
