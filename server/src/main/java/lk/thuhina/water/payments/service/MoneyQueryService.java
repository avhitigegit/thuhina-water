package lk.thuhina.water.payments.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.payments.dto.MoneyDtos.MoneyList;
import lk.thuhina.water.payments.dto.MoneyDtos.MoneyRow;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Permissions;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Payments page: every rupee in one place. Money in = customer payments and money paid on bills (Paid now on a sale,
 * Enter Bills, My Deliveries, registration). Money out = supplier payments, factory payments and expenses. Only reads –
 * payments are saved by their own services (billing, purchasing, production, expenses).
 */
@Service
public class MoneyQueryService {

    public static final int LIMIT = 500;

    private static final Map<String, String> KIND_LABELS = Map.of(
            "PAYMENT", "Payment received",
            "BILL", "Paid on bill",
            "SUPPLIER", "Supplier payment",
            "FACTORY", "Factory payment",
            "EXPENSE", "Expense");

    private static final String IN_PAYMENTS = "select 'PAYMENT' as kind, p.id, p.receipt_no as ref, p.pay_date as d, p.created_at as at, "
            + "p.customer_id as party_id, c.name as party, p.method, p.reference, p.note, p.amount, p.created_by "
            + "from customer_payment p join customer c on c.id = p.customer_id";
    private static final String IN_BILLS = "select 'BILL', t.id, t.bill_no, t.txn_date, t.created_at, t.customer_id, "
            + "coalesce(c.name, t.walk_in_name, 'Walk-in'), coalesce(t.pay_method, 'CASH'), t.pay_reference, null, t.paid_on_bill, "
            + "t.created_by from sales_txn t left join customer c on c.id = t.customer_id where t.paid_on_bill > 0";
    private static final String OUT_SUPPLIERS = "select 'SUPPLIER' as kind, p.id, p.sp_no as ref, p.pay_date as d, p.created_at as at, "
            + "p.supplier_id as party_id, s.name as party, p.method, p.reference, p.note, p.amount, p.created_by "
            + "from supplier_payment p join supplier s on s.id = p.supplier_id";
    private static final String OUT_FACTORIES = "select 'FACTORY', p.id, p.fp_no, p.pay_date, p.created_at, p.factory_id, f.name, p.method, "
            + "p.reference, p.note, p.amount, p.created_by from factory_payment p join factory f on f.id = p.factory_id";
    private static final String OUT_EXPENSES = "select 'EXPENSE', e.id, e.expense_no, e.paid_date, e.created_at, cast(null as bigint), "
            + "coalesce(nullif(e.paid_to, ''), t.name), e.method, e.reference, "
            + "t.name || coalesce(' – ' || nullif(e.description, ''), ''), e.amount, e.created_by "
            + "from expense e join expense_type t on t.id = e.expense_type_id where not e.deleted";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public MoneyQueryService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Money coming in: customer payments and money paid on bills. */
    @Transactional(readOnly = true)
    public MoneyList moneyIn(LocalDate from, LocalDate to, String q) {
        return list(List.of(IN_PAYMENTS, IN_BILLS), from, to, q);
    }

    /** Money going out: supplier and factory payments and expenses – each only for a user who may see that page. */
    @Transactional(readOnly = true)
    public MoneyList moneyOut(LocalDate from, LocalDate to, String q) {
        List<String> parts = new ArrayList<>();
        if (CurrentUser.can(Permissions.PURCHASING_VIEW)) {
            parts.add(OUT_SUPPLIERS);
        }
        if (CurrentUser.can(Permissions.PRODUCTION_VIEW)) {
            parts.add(OUT_FACTORIES);
        }
        if (CurrentUser.can(Permissions.EXPENSES_VIEW)) {
            parts.add(OUT_EXPENSES);
        }
        return list(parts, from, to, q);
    }

    private MoneyList list(List<String> parts, LocalDate fromParam, LocalDate toParam, String q) {
        LocalDate to = toParam == null ? BusinessDates.today(clock) : toParam;
        LocalDate from = fromParam == null ? to.withDayOfMonth(1) : fromParam;
        if (from.isAfter(to)) {
            throw new ValidationException("from", "The From date is after the To date.");
        }
        if (parts.isEmpty()) {
            return new MoneyList(from, to, List.of(), 0, Money.of(BigDecimal.ZERO), LIMIT);
        }
        String text = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase() + "%";
        String rows = "select x.*, coalesce(u.full_name, x.created_by) as entered_by from (" + String.join(" union all ", parts)
                + ") x left join app_user u on u.username = x.created_by where x.d between :from and :to"
                + (text == null ? "" : " and (lower(x.party) like :text or lower(x.ref) like :text"
                        + " or lower(coalesce(x.reference, '')) like :text or lower(coalesce(x.note, '')) like :text)");
        MapSqlParameterSource p = new MapSqlParameterSource("from", from).addValue("to", to).addValue("text", text).addValue("n", LIMIT);
        List<MoneyRow> items = jdbc.query(rows + " order by x.d desc, x.at desc, x.id desc limit :n", p, (rs, i) -> {
            String kind = rs.getString("kind");
            return new MoneyRow(kind, KIND_LABELS.get(kind), rs.getLong("id"), rs.getString("ref"), rs.getObject("d", LocalDate.class),
                    rs.getTimestamp("at").toInstant(), (Long) rs.getObject("party_id"), rs.getString("party"),
                    PayMethod.valueOf(rs.getString("method")).label(), rs.getString("reference"), rs.getString("note"),
                    Money.of(rs.getBigDecimal("amount")), rs.getString("entered_by"));
        });
        Map<String, Object> sum = jdbc.queryForMap("select count(*) as n, coalesce(sum(amount), 0) as total from (" + rows + ") y", p);
        return new MoneyList(from, to, items, ((Number) sum.get("n")).longValue(), Money.of((BigDecimal) sum.get("total")), LIMIT);
    }
}
