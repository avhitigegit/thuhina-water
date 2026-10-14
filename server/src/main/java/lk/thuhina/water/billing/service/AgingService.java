package lk.thuhina.water.billing.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.thuhina.water.billing.dto.BillingDtos.AccountListResponse;
import lk.thuhina.water.billing.dto.BillingDtos.AccountRow;
import lk.thuhina.water.billing.dto.BillingDtos.AccountSummary;
import lk.thuhina.water.billing.dto.BillingDtos.AgingTotals;
import lk.thuhina.water.billing.rules.AgingRules;
import lk.thuhina.water.billing.rules.AgingRules.Aging;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.customers.model.PayType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer accounts with aging (design 5.6, FR-48, FR-51). The lists age every customer in one SQL query: a bill's unpaid
 * part = its net clamped between 0 and (running Σ of the customer's charges, oldest first − all credits) – the same FIFO as
 * {@link AgingRules}, which is used for one customer (the tests check both agree).
 */
@Service
public class AgingService {

    public static final int MAX_PAGE_SIZE = 200;

    /** Filters: search (name, code, phone), pay type, show {@code over} (over the credit limit) / {@code old} (older than 30 days). */
    public record Filter(String q, PayType payType, String show) {
    }

    /** Every customer's buckets and balance on :asOf (balance ≠ 0 rows are kept by the callers). */
    private static final String AGED = """
            with nets as (
                select customer_id, id, txn_date as d, amount - paid_on_bill as net
                from sales_txn where customer_id is not null and txn_date <= :asOf
            ), credit as (
                select customer_id, sum(c) as credit from (
                    select customer_id, -net as c from nets where net < 0
                    union all
                    select customer_id, amount from customer_payment where pay_date <= :asOf
                ) x group by customer_id
            ), charged as (
                select n.customer_id, n.d, n.net, sum(n.net) over (partition by n.customer_id order by n.d, n.id) as cum
                from nets n where n.net > 0
            ), aged as (
                select ch.customer_id, sum(ch.net) as charges,
                       sum(x.rem) filter (where cast(:asOf as date) - ch.d <= 30) as b0,
                       sum(x.rem) filter (where cast(:asOf as date) - ch.d between 31 and 60) as b30,
                       sum(x.rem) filter (where cast(:asOf as date) - ch.d between 61 and 90) as b60,
                       sum(x.rem) filter (where cast(:asOf as date) - ch.d > 90) as b90,
                       min(ch.d) filter (where x.rem > 0) as oldest
                from charged ch left join credit cr on cr.customer_id = ch.customer_id
                cross join lateral (select greatest(0, least(ch.net, ch.cum - coalesce(cr.credit, 0))) as rem) x
                group by ch.customer_id
            ), acc as (
                select coalesce(a.customer_id, cr.customer_id) as customer_id,
                       coalesce(a.b0, 0) as b0, coalesce(a.b30, 0) as b30, coalesce(a.b60, 0) as b60, coalesce(a.b90, 0) as b90,
                       coalesce(a.charges, 0) - coalesce(cr.credit, 0) as balance, a.oldest
                from aged a full join credit cr on cr.customer_id = a.customer_id
            )
            select c.id, c.code, c.name, c.phone, c.pay_type, c.credit_limit, c.status, acc.b0, acc.b30, acc.b60, acc.b90, acc.balance,
                   acc.oldest, (c.pay_type <> 'CASH' and acc.balance > c.credit_limit) as over_limit
            from acc join customer c on c.id = acc.customer_id
            where acc.balance <> 0
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AgingService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Accounts with a balance, largest first, one page, with the totals of every matching row. */
    @Transactional(readOnly = true)
    public AccountListResponse accounts(Filter f, LocalDate asOf, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        MapSqlParameterSource p = new MapSqlParameterSource("asOf", date(asOf));
        StringBuilder where = new StringBuilder("true");
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            String digits = q.replaceAll("\\D", "");
            where.append(" and (lower(name) like :q escape '\\' or lower(code) like :q escape '\\'");
            p.addValue("q", "%" + likeEscape(q) + "%");
            if (digits.length() >= 3 && q.replaceAll("[\\d\\s]", "").isEmpty()) {
                where.append(" or regexp_replace(phone, '[^0-9]', '', 'g') like :digits");
                p.addValue("digits", "%" + digits + "%");
            }
            where.append(")");
        }
        if (f.payType() != null) {
            where.append(" and pay_type = :pay");
            p.addValue("pay", f.payType().name());
        }
        if ("over".equalsIgnoreCase(f.show())) {
            where.append(" and over_limit");
        } else if ("old".equalsIgnoreCase(f.show())) {
            where.append(" and b30 + b60 + b90 > 0");
        }
        String sql = "select r.*, count(*) over () as n, sum(b0) over () as t0, sum(b30) over () as t30, sum(b60) over () as t60, "
                + "sum(b90) over () as t90, sum(balance) over () as tb from (" + AGED + ") r where " + where
                + " order by balance desc, lower(name), id limit :limit offset :offset";
        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        List<AccountRow> items = new ArrayList<>();
        Object[] totals = {0L, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO};
        jdbc.query(sql, p, rs -> {
            items.add(row(rs));
            totals[0] = rs.getLong("n");
            totals[1] = Money.of(rs.getBigDecimal("t0"));
            totals[2] = Money.of(rs.getBigDecimal("t30"));
            totals[3] = Money.of(rs.getBigDecimal("t60"));
            totals[4] = Money.of(rs.getBigDecimal("t90"));
            totals[5] = Money.of(rs.getBigDecimal("tb"));
        });
        long total = (long) totals[0];
        if (items.isEmpty() && safePage > 0) {
            // A page past the end: still return the totals.
            AccountListResponse first = accounts(f, asOf, 0, safeSize);
            return new AccountListResponse(List.of(), safePage, safeSize, first.total(), first.totals());
        }
        return new AccountListResponse(items, safePage, safeSize, total, new AgingTotals((BigDecimal) totals[1], (BigDecimal) totals[2],
                (BigDecimal) totals[3], (BigDecimal) totals[4], (BigDecimal) totals[5]));
    }

    /** KPIs over every customer with a balance. */
    @Transactional(readOnly = true)
    public AccountSummary summary(LocalDate asOf) {
        LocalDate on = date(asOf);
        return jdbc.queryForObject("select count(*) as n, count(*) filter (where over_limit) as over_n, coalesce(sum(balance), 0) as tb, "
                        + "coalesce(sum(b0), 0) as t0, coalesce(sum(b30), 0) as t30, coalesce(sum(b60), 0) as t60, coalesce(sum(b90), 0) as t90 "
                        + "from (" + AGED + ") r", new MapSqlParameterSource("asOf", on),
                (rs, i) -> new AccountSummary(Money.of(rs.getBigDecimal("tb")), Money.of(rs.getBigDecimal("t0")),
                        Money.of(rs.getBigDecimal("t30")), Money.of(rs.getBigDecimal("t60")), Money.of(rs.getBigDecimal("t90")),
                        rs.getLong("n"), rs.getLong("over_n"), on));
    }

    /** One customer's account on :asOf (also when the balance is zero), aged with {@link AgingRules}. */
    @Transactional(readOnly = true)
    public AccountRow account(long customerId, LocalDate asOf) {
        LocalDate on = date(asOf);
        MapSqlParameterSource p = new MapSqlParameterSource("c", customerId).addValue("asOf", on);
        List<Object[]> c = jdbc.query("select id, code, name, phone, pay_type, credit_limit, status from customer where id = :c", p,
                (rs, i) -> new Object[] {rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("pay_type"), rs.getBigDecimal("credit_limit"), rs.getString("status")});
        if (c.isEmpty()) {
            throw new NotFoundException("Customer", customerId);
        }
        Aging a = aging(customerId, on);
        Object[] r = c.getFirst();
        PayType pay = PayType.valueOf((String) r[4]);
        BigDecimal limit = Money.of((BigDecimal) r[5]);
        return new AccountRow((long) r[0], (String) r[1], (String) r[2], (String) r[3], pay.name(), pay.label(), limit, (String) r[6],
                a.days0to30(), a.days31to60(), a.days61to90(), a.over90(), a.total(), pay != PayType.CASH && a.total().compareTo(limit) > 0,
                a.oldest());
    }

    /** {@link AgingRules#age} over the customer's bills and payments up to :asOf. */
    @Transactional(readOnly = true)
    public Aging aging(long customerId, LocalDate asOf) {
        MapSqlParameterSource p = new MapSqlParameterSource("c", customerId).addValue("asOf", asOf);
        List<AgingRules.Bill> bills = jdbc.query("select txn_date, amount - paid_on_bill as net from sales_txn "
                        + "where customer_id = :c and txn_date <= :asOf order by txn_date, id", p,
                (rs, i) -> new AgingRules.Bill(rs.getObject("txn_date", LocalDate.class), rs.getBigDecimal("net")));
        BigDecimal paid = jdbc.queryForObject("select coalesce(sum(amount), 0) from customer_payment where customer_id = :c "
                + "and pay_date <= :asOf", p, BigDecimal.class);
        return AgingRules.age(bills, paid, asOf);
    }

    private static AccountRow row(ResultSet rs) throws SQLException {
        PayType pay = PayType.valueOf(rs.getString("pay_type"));
        return new AccountRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("phone"), pay.name(), pay.label(),
                Money.of(rs.getBigDecimal("credit_limit")), rs.getString("status"), Money.of(rs.getBigDecimal("b0")),
                Money.of(rs.getBigDecimal("b30")), Money.of(rs.getBigDecimal("b60")), Money.of(rs.getBigDecimal("b90")),
                Money.of(rs.getBigDecimal("balance")), rs.getBoolean("over_limit"), rs.getObject("oldest", LocalDate.class));
    }

    private LocalDate date(LocalDate asOf) {
        return asOf == null ? BusinessDates.today(clock) : asOf;
    }

    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
