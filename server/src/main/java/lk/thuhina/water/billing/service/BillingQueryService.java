package lk.thuhina.water.billing.service;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.billing.dto.BillingDtos.CustomerInfo;
import lk.thuhina.water.billing.dto.BillingDtos.CustomerLink;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceLine;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceLink;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceMonthRow;
import lk.thuhina.water.billing.dto.BillingDtos.InvoicePreview;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceRow;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceView;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentListResponse;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRow;
import lk.thuhina.water.billing.dto.BillingDtos.PeriodOption;
import lk.thuhina.water.billing.dto.BillingDtos.RecentMoney;
import lk.thuhina.water.billing.dto.BillingDtos.StatementRow;
import lk.thuhina.water.billing.dto.BillingDtos.StatementView;
import lk.thuhina.water.billing.rules.AgingRules.Aging;
import lk.thuhina.water.billing.rules.InvoiceRules;
import lk.thuhina.water.billing.rules.InvoiceRules.InvoiceTotals;
import lk.thuhina.water.billing.rules.PaymentRules;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing reads (FR-47, FR-49, FR-50, FR-52): payments, recent money in (payments and cash paid on bills), the Monthly
 * invoices tab of a month, invoices with their lines, and statements. Plain SQL.
 */
@Service
public class BillingQueryService {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int RECENT = 15;

    private static final String PAYMENTS = "select p.id, p.receipt_no, p.pay_date, p.customer_id, c.code, c.name, p.amount, p.method, "
            + "p.reference, p.bank, p.cheque_date, p.invoice_id, i.invoice_no, p.note, p.balance_before, p.created_by, p.created_at "
            + "from customer_payment p join customer c on c.id = p.customer_id left join monthly_invoice i on i.id = p.invoice_id ";

    private static final String INVOICES = "select i.id, i.invoice_no, i.customer_id, c.code, c.name, i.period, i.invoice_date, i.due_date, "
            + "i.opening_balance, i.charges, i.paid_on_bills, i.payments, i.amount, i.total_due, i.period_from, i.period_to, i.created_by, "
            + "(select count(*) from invoice_txn x where x.invoice_id = i.id) as deliveries, "
            + "(select coalesce(sum(p.amount), 0) from customer_payment p where p.invoice_id = i.id) as paid "
            + "from monthly_invoice i join customer c on c.id = i.customer_id ";

    private final NamedParameterJdbcTemplate jdbc;
    private final AgingService aging;
    private final Clock clock;

    public BillingQueryService(NamedParameterJdbcTemplate jdbc, AgingService aging, Clock clock) {
        this.jdbc = jdbc;
        this.aging = aging;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ payments (FR-50)

    /** Payments newest first; customer and dates optional. */
    @Transactional(readOnly = true)
    public PaymentListResponse payments(Long customerId, LocalDate from, LocalDate to, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        StringBuilder where = new StringBuilder("where true");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (customerId != null) {
            where.append(" and p.customer_id = :c");
            p.addValue("c", customerId);
        }
        if (from != null) {
            where.append(" and p.pay_date >= :from");
            p.addValue("from", from);
        }
        if (to != null) {
            where.append(" and p.pay_date <= :to");
            p.addValue("to", to);
        }
        Long total = jdbc.queryForObject("select count(*) from customer_payment p " + where, p, Long.class);
        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        List<PaymentRow> items = paymentRows(PAYMENTS + where + " order by p.pay_date desc, p.id desc limit :limit offset :offset", p);
        return new PaymentListResponse(items, safePage, safeSize, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public PaymentRow payment(long id) {
        List<PaymentRow> rows = paymentRows(PAYMENTS + "where p.id = :id", new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            throw new NotFoundException("Payment", id);
        }
        return rows.getFirst();
    }

    /** The latest money in (FR-47): payments and cash paid on bills (including walk-in sales), newest first. */
    @Transactional(readOnly = true)
    public List<RecentMoney> recent(int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select * from ("
                        + "select 'PAYMENT' as kind, p.id, p.receipt_no as ref, p.pay_date as d, p.created_at as at, p.customer_id, c.name, "
                        + "p.method, p.reference, null as bill_no, p.amount from customer_payment p join customer c on c.id = p.customer_id "
                        + "union all "
                        + "select 'BILL', t.id, t.bill_no, t.txn_date, t.created_at, t.customer_id, coalesce(c.name, t.walk_in_name), t.pay_method, "
                        + "null, t.bill_no, t.paid_on_bill from sales_txn t left join customer c on c.id = t.customer_id where t.paid_on_bill > 0"
                        + ") x order by d desc, at desc, id desc limit :n", new MapSqlParameterSource("n", n),
                (rs, i) -> {
                    String how;
                    if ("PAYMENT".equals(rs.getString("kind"))) {
                        how = PayMethod.valueOf(rs.getString("method")).label()
                                + (rs.getString("reference") == null ? "" : " – " + rs.getString("reference"));
                    } else {
                        String m = rs.getString("method");
                        how = (m == null || PayMethod.CASH.name().equals(m) ? "Cash on delivery" : PayMethod.valueOf(m).label() + " on delivery")
                                + " – bill " + rs.getString("bill_no");
                    }
                    return new RecentMoney(rs.getString("kind"), rs.getLong("id"), rs.getString("ref"), rs.getObject("d", LocalDate.class),
                            rs.getTimestamp("at").toInstant(), (Long) rs.getObject("customer_id"), rs.getString("name"), how,
                            Money.of(rs.getBigDecimal("amount")));
                });
    }

    // ------------------------------------------------------------------ monthly invoices (FR-49)

    /** The Monthly invoices tab: every Monthly bill customer (and anyone already invoiced) for the month. */
    @Transactional(readOnly = true)
    public InvoicePreview preview(String period) {
        LocalDate today = BusinessDates.today(clock);
        String p = period == null || period.isBlank() ? InvoiceRules.defaultPeriod(today) : period.trim();
        if (!InvoiceRules.isPeriod(p)) {
            throw new ValidationException("period", "Choose the month.");
        }
        List<InvoiceMonthRow> rows = monthRows(p, null);
        List<PeriodOption> periods = InvoiceRules.periods(today).stream()
                .map(x -> new PeriodOption(x, InvoiceRules.periodLabel(x))).toList();
        int toGenerate = (int) rows.stream().filter(r -> r.invoice() == null).count();
        return new InvoicePreview(p, InvoiceRules.periodLabel(p), periods, rows, toGenerate);
    }

    /** One row per customer for the month; {@code customerId} limits it to one customer. */
    @Transactional(readOnly = true)
    public List<InvoiceMonthRow> monthRows(String period, Long customerId) {
        YearMonth m = YearMonth.parse(period);
        MapSqlParameterSource p = new MapSqlParameterSource("period", period).addValue("from", m.atDay(1)).addValue("to", m.atEndOfMonth());
        String only = "";
        if (customerId != null) {
            only = " and c.id = :c";
            p.addValue("c", customerId);
        }
        return jdbc.query("select c.id, c.code, c.name, c.status, i.id as inv_id, i.invoice_no, i.total_due as inv_total, "
                        + "(select count(*) from sales_txn t where t.customer_id = c.id and t.txn_date between :from and :to "
                        + "  and t.kind <> 'OPENING_BALANCE') as deliveries, "
                        + "(select coalesce(sum(t.amount), 0) from sales_txn t where t.customer_id = c.id and t.txn_date between :from and :to "
                        + "  and t.kind <> 'OPENING_BALANCE') as charges, "
                        + "(select coalesce(sum(t.paid_on_bill), 0) from sales_txn t where t.customer_id = c.id "
                        + "  and t.txn_date between :from and :to and t.kind <> 'OPENING_BALANCE') as paid, "
                        + "(select coalesce(sum(t.amount - t.paid_on_bill), 0) from sales_txn t where t.customer_id = c.id and (t.txn_date < :from "
                        + "  or (t.kind = 'OPENING_BALANCE' and t.txn_date <= :to))) "
                        + " - (select coalesce(sum(x.amount), 0) from customer_payment x where x.customer_id = c.id and x.pay_date < :from) as opening, "
                        + "(select coalesce(sum(x.amount), 0) from customer_payment x where x.customer_id = c.id "
                        + "  and x.pay_date between :from and :to) as payments "
                        + "from customer c left join monthly_invoice i on i.customer_id = c.id and i.period = :period "
                        + "where (c.pay_type = 'MONTHLY_BILL' or i.id is not null)" + only + " order by lower(c.name), c.id", p,
                (rs, i) -> {
                    InvoiceTotals t = InvoiceTotals.of(rs.getBigDecimal("opening"), rs.getBigDecimal("charges"), rs.getBigDecimal("paid"),
                            rs.getBigDecimal("payments"));
                    InvoiceLink inv = rs.getObject("inv_id") == null ? null : new InvoiceLink(rs.getLong("inv_id"), rs.getString("invoice_no"),
                            period, InvoiceRules.periodLabel(period), Money.of(rs.getBigDecimal("inv_total")));
                    return new InvoiceMonthRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("status"),
                            rs.getInt("deliveries"), t.charges(), t.paidOnBills(), t.amount(), t.opening(), t.payments(),
                            inv == null ? t.totalDue() : inv.totalDue(), inv);
                });
    }

    /** Invoices newest first; period and customer optional. */
    @Transactional(readOnly = true)
    public List<InvoiceRow> invoices(String period, Long customerId) {
        StringBuilder where = new StringBuilder("where true");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (period != null && !period.isBlank()) {
            where.append(" and i.period = :period");
            p.addValue("period", period.trim());
        }
        if (customerId != null) {
            where.append(" and i.customer_id = :c");
            p.addValue("c", customerId);
        }
        return invoiceRows(INVOICES + where + " order by i.period desc, i.invoice_no desc", p);
    }

    @Transactional(readOnly = true)
    public InvoiceRow invoiceRow(long id) {
        List<InvoiceRow> rows = invoiceRows(INVOICES + "where i.id = :id", new MapSqlParameterSource("id", id));
        if (rows.isEmpty()) {
            throw new NotFoundException("Invoice", id);
        }
        return rows.getFirst();
    }

    /** The customer's invoices as links, newest first (for "Against invoice"). */
    @Transactional(readOnly = true)
    public List<InvoiceLink> invoiceLinks(long customerId) {
        return jdbc.query("select id, invoice_no, period, total_due from monthly_invoice where customer_id = :c order by period desc, id desc",
                new MapSqlParameterSource("c", customerId),
                (rs, i) -> new InvoiceLink(rs.getLong("id"), rs.getString("invoice_no"), rs.getString("period"),
                        InvoiceRules.periodLabel(rs.getString("period")), Money.of(rs.getBigDecimal("total_due"))));
    }

    /** An invoice with a line per delivery, deposit, damage replacement, product and "Paid on delivery". */
    @Transactional(readOnly = true)
    public InvoiceView invoice(long id) {
        InvoiceRow r = invoiceRow(id);
        Map<String, Object> dates = jdbc.queryForMap("select period_from, period_to from monthly_invoice where id = :id",
                new MapSqlParameterSource("id", id));
        BigDecimal charges = jdbc.queryForObject("select charges from monthly_invoice where id = :id", new MapSqlParameterSource("id", id),
                BigDecimal.class);
        BigDecimal paidOnBills = Money.of(charges).subtract(r.amount());
        return new InvoiceView(r.id(), r.invoiceNo(), customerInfo(r.customer().id()), r.period(), r.periodLabel(),
                ((java.sql.Date) dates.get("period_from")).toLocalDate(), ((java.sql.Date) dates.get("period_to")).toLocalDate(),
                r.invoiceDate(), r.dueDate(), r.deliveries(), r.opening(), Money.of(charges), paidOnBills, r.payments(), r.amount(),
                r.totalDue(), r.paid(), invoiceLines(id), r.createdByName());
    }

    /** The lines of an invoice's bills; any part of a bill not covered by a line is shown as "Other charges". */
    private List<InvoiceLine> invoiceLines(long invoiceId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", invoiceId);
        Map<Long, List<Object[]>> linesByTxn = new HashMap<>();
        jdbc.query("select l.txn_id, l.bottle_type_code, bt.name as bt_name, l.product_code, pr.name as pr_name, l.filled, l.unit_price, "
                + "l.deposit_unit, l.deposit_count, l.qty, l.line_amount from sales_txn_line l join invoice_txn x on x.txn_id = l.txn_id "
                + "left join bottle_type bt on bt.code = l.bottle_type_code left join product pr on pr.code = l.product_code "
                + "where x.invoice_id = :id order by l.txn_id, l.line_no, l.id", p, rs -> {
            linesByTxn.computeIfAbsent(rs.getLong("txn_id"), k -> new ArrayList<>()).add(new Object[] {rs.getString("bottle_type_code"),
                    rs.getString("bt_name"), rs.getString("product_code"), rs.getString("pr_name"), rs.getInt("filled"),
                    rs.getBigDecimal("unit_price"), rs.getBigDecimal("deposit_unit"), rs.getInt("deposit_count"), rs.getInt("qty"),
                    rs.getBigDecimal("line_amount")});
        });
        List<InvoiceLine> out = new ArrayList<>();
        jdbc.query("select t.id, t.bill_no, t.kind, t.txn_date, t.amount, t.paid_on_bill, t.note from sales_txn t "
                + "join invoice_txn x on x.txn_id = t.id where x.invoice_id = :id order by t.txn_date, t.id", p, rs -> {
            long txnId = rs.getLong("id");
            String bill = rs.getString("bill_no");
            LocalDate d = rs.getObject("txn_date", LocalDate.class);
            SalesKind kind = SalesKind.valueOf(rs.getString("kind"));
            BigDecimal amount = Money.of(rs.getBigDecimal("amount"));
            BigDecimal paid = Money.of(rs.getBigDecimal("paid_on_bill"));
            BigDecimal covered = Money.ZERO;
            if (kind == SalesKind.REVERSAL) {
                if (amount.signum() != 0) {
                    out.add(new InvoiceLine(d, bill, rs.getString("note") == null ? "Reversal" : rs.getString("note"), null, null, amount));
                    covered = amount;
                }
            } else {
                for (Object[] l : linesByTxn.getOrDefault(txnId, List.of())) {
                    if (l[2] != null) {
                        BigDecimal v = Money.of((BigDecimal) l[9]);
                        out.add(new InvoiceLine(d, bill, (String) l[3], (int) l[8], (BigDecimal) l[5], v));
                        covered = covered.add(v);
                        continue;
                    }
                    int filled = (int) l[4];
                    BigDecimal price = (BigDecimal) l[5];
                    if (filled > 0 && price != null) {
                        BigDecimal v = Money.times(price, filled);
                        out.add(new InvoiceLine(d, bill, "Water " + l[1] + (kind == SalesKind.LEFT_AT_DOOR ? " (left at door)" : ""), filled,
                                Money.of(price), v));
                        covered = covered.add(v);
                    }
                    int deposits = (int) l[7];
                    BigDecimal unit = (BigDecimal) l[6];
                    if (deposits > 0 && unit != null) {
                        BigDecimal v = Money.times(unit, deposits);
                        out.add(new InvoiceLine(d, bill, kind == SalesKind.CUSTOMER_DAMAGE ? "New bottle – customer damage" : "Bottle deposit",
                                deposits, Money.of(unit), v));
                        covered = covered.add(v);
                    }
                }
                BigDecimal rest = amount.subtract(covered);
                if (rest.signum() != 0) {
                    out.add(new InvoiceLine(d, bill, kind.label() + " – other charges", null, null, rest));
                }
            }
            if (paid.signum() != 0) {
                out.add(new InvoiceLine(d, bill, paid.signum() > 0 ? "Paid on delivery" : "Paid on delivery – reversed", null, null,
                        paid.negate()));
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ statement (FR-52)

    /** Brought forward on {@code from}, every charge and payment to {@code to} with the running balance, and the aging on {@code to}. */
    @Transactional(readOnly = true)
    public StatementView statement(long customerId, LocalDate from, LocalDate to) {
        LocalDate today = BusinessDates.today(clock);
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? YearMonth.from(today).minusMonths(1).atDay(1) : from;
        if (f.isAfter(t)) {
            throw new ValidationException("from", "From date cannot be after the to date.");
        }
        CustomerInfo c = customerInfo(customerId);
        MapSqlParameterSource p = new MapSqlParameterSource("c", customerId).addValue("from", f).addValue("to", t);
        BigDecimal open = Money.of(jdbc.queryForObject("select (select coalesce(sum(amount - paid_on_bill), 0) from sales_txn "
                + "where customer_id = :c and txn_date < :from) - (select coalesce(sum(amount), 0) from customer_payment "
                + "where customer_id = :c and pay_date < :from)", p, BigDecimal.class));
        record Entry(LocalDate date, java.time.Instant at, long order, String ref, String text, BigDecimal dr, BigDecimal cr) {
        }
        List<Entry> entries = new ArrayList<>();
        Map<Long, String> lines = billSummaries(customerId, f, t);
        jdbc.query("select id, bill_no, kind, txn_date, amount, paid_on_bill, created_at from sales_txn where customer_id = :c "
                + "and txn_date between :from and :to and (amount <> 0 or paid_on_bill <> 0)", p, rs -> {
            BigDecimal amount = Money.of(rs.getBigDecimal("amount"));
            BigDecimal paid = Money.of(rs.getBigDecimal("paid_on_bill"));
            BigDecimal dr = amount.max(Money.ZERO).add(paid.negate().max(Money.ZERO));
            BigDecimal cr = amount.negate().max(Money.ZERO).add(paid.max(Money.ZERO));
            String text = SalesKind.valueOf(rs.getString("kind")).label() + lines.getOrDefault(rs.getLong("id"), "");
            entries.add(new Entry(rs.getObject("txn_date", LocalDate.class), rs.getTimestamp("created_at").toInstant(), rs.getLong("id"),
                    rs.getString("bill_no"), text, dr, cr));
        });
        jdbc.query("select id, receipt_no, pay_date, amount, method, reference, created_at from customer_payment where customer_id = :c "
                + "and pay_date between :from and :to", p, rs -> {
            entries.add(new Entry(rs.getObject("pay_date", LocalDate.class), rs.getTimestamp("created_at").toInstant(), rs.getLong("id"),
                    rs.getString("receipt_no"), "Payment – " + PaymentRules.how(PayMethod.valueOf(rs.getString("method")), rs.getString("reference")),
                    Money.ZERO, Money.of(rs.getBigDecimal("amount"))));
        });
        entries.sort(Comparator.comparing(Entry::date).thenComparing(Entry::at).thenComparing(Entry::order));
        List<StatementRow> rows = new ArrayList<>();
        BigDecimal run = open;
        for (Entry e : entries) {
            run = run.add(e.dr()).subtract(e.cr());
            rows.add(new StatementRow(e.date(), e.ref(), e.text(), e.dr(), e.cr(), run));
        }
        Aging a = aging.aging(customerId, t);
        return new StatementView(c, f, t, open, rows, run, a.days0to30(), a.days31to60(), a.days61to90(), a.over90());
    }

    /** " 2 × 20L, 1 × 10L" (filled bottles) per bill of the customer in the dates. */
    private Map<Long, String> billSummaries(long customerId, LocalDate from, LocalDate to) {
        Map<Long, List<String>> parts = new HashMap<>();
        jdbc.query("select l.txn_id, l.filled, bt.litres from sales_txn_line l join sales_txn t on t.id = l.txn_id "
                        + "join bottle_type bt on bt.code = l.bottle_type_code where t.customer_id = :c and t.txn_date between :from and :to "
                        + "and l.filled > 0 order by l.txn_id, l.line_no",
                new MapSqlParameterSource("c", customerId).addValue("from", from).addValue("to", to), rs -> {
                    parts.computeIfAbsent(rs.getLong("txn_id"), k -> new ArrayList<>())
                            .add(rs.getInt("filled") + " × " + LedgerRules.bottleLabel(rs.getBigDecimal("litres")));
                });
        Map<Long, String> out = new HashMap<>();
        parts.forEach((k, v) -> out.put(k, " " + String.join(", ", v)));
        return out;
    }

    @Transactional(readOnly = true)
    public CustomerInfo customerInfo(long customerId) {
        List<CustomerInfo> c = jdbc.query("select c.id, c.code, c.name, c.address, a.name as area, c.phone, c.pay_type, c.terms_days "
                        + "from customer c join area a on a.id = c.area_id where c.id = :c", new MapSqlParameterSource("c", customerId),
                (rs, i) -> {
                    PayType pay = PayType.valueOf(rs.getString("pay_type"));
                    return new CustomerInfo(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"),
                            rs.getString("area"), rs.getString("phone"), pay.name(), pay.label(), rs.getInt("terms_days"));
                });
        if (c.isEmpty()) {
            throw new NotFoundException("Customer", customerId);
        }
        return c.getFirst();
    }

    // ------------------------------------------------------------------ helpers

    private List<PaymentRow> paymentRows(String sql, MapSqlParameterSource p) {
        List<Object[]> raw = jdbc.query(sql, p, (rs, i) -> new Object[] {payment(rs), rs.getString("created_by")});
        Map<String, String> names = userNames(raw.stream().map(r -> (String) r[1]).toList());
        return raw.stream().map(r -> {
            PaymentRow x = (PaymentRow) r[0];
            return new PaymentRow(x.id(), x.receiptNo(), x.date(), x.customer(), x.amount(), x.method(), x.methodLabel(), x.reference(),
                    x.bank(), x.chequeDate(), x.invoiceId(), x.invoiceNo(), x.note(), x.balanceBefore(), x.balanceAfter(), x.partPayment(),
                    names.get((String) r[1]), x.createdAt());
        }).toList();
    }

    private static PaymentRow payment(ResultSet rs) throws SQLException {
        PayMethod m = PayMethod.valueOf(rs.getString("method"));
        BigDecimal amount = Money.of(rs.getBigDecimal("amount"));
        BigDecimal before = Money.of(rs.getBigDecimal("balance_before"));
        return new PaymentRow(rs.getLong("id"), rs.getString("receipt_no"), rs.getObject("pay_date", LocalDate.class),
                new CustomerLink(rs.getLong("customer_id"), rs.getString("code"), rs.getString("name")), amount, m.name(), m.label(),
                rs.getString("reference"), rs.getString("bank"), rs.getObject("cheque_date", LocalDate.class), (Long) rs.getObject("invoice_id"),
                rs.getString("invoice_no"), rs.getString("note"), before, before.subtract(amount), PaymentRules.isPartPayment(amount, before),
                null, rs.getTimestamp("created_at").toInstant());
    }

    private List<InvoiceRow> invoiceRows(String sql, MapSqlParameterSource p) {
        List<Object[]> raw = jdbc.query(sql, p, (rs, i) -> new Object[] {new InvoiceRow(rs.getLong("id"), rs.getString("invoice_no"),
                new CustomerLink(rs.getLong("customer_id"), rs.getString("code"), rs.getString("name")), rs.getString("period"),
                InvoiceRules.periodLabel(rs.getString("period")), rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class), rs.getInt("deliveries"), Money.of(rs.getBigDecimal("opening_balance")),
                Money.of(rs.getBigDecimal("payments")), Money.of(rs.getBigDecimal("amount")), Money.of(rs.getBigDecimal("total_due")),
                Money.of(rs.getBigDecimal("paid")), null), rs.getString("created_by")});
        Map<String, String> names = userNames(raw.stream().map(r -> (String) r[1]).toList());
        return raw.stream().map(r -> {
            InvoiceRow x = (InvoiceRow) r[0];
            return new InvoiceRow(x.id(), x.invoiceNo(), x.customer(), x.period(), x.periodLabel(), x.invoiceDate(), x.dueDate(),
                    x.deliveries(), x.opening(), x.payments(), x.amount(), x.totalDue(), x.paid(), names.get((String) r[1]));
        }).toList();
    }

    /** Full names of users; usernames without a user (seed, system) are shown as they are. */
    private Map<String, String> userNames(Collection<String> usernames) {
        Set<String> wanted = new HashSet<>();
        usernames.forEach(u -> {
            if (u != null) {
                wanted.add(u);
            }
        });
        Map<String, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted), rs -> {
                out.put(rs.getString("username"), rs.getString("full_name"));
            });
        }
        wanted.forEach(u -> out.putIfAbsent(u, u));
        return out;
    }
}
