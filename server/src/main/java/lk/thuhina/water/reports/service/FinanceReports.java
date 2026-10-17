package lk.thuhina.water.reports.service;

import static lk.thuhina.water.reports.service.ReportBuilder.DATE;
import static lk.thuhina.water.reports.service.ReportBuilder.MONEY;
import static lk.thuhina.water.reports.service.ReportBuilder.NUMBER;
import static lk.thuhina.water.reports.service.ReportBuilder.TEXT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.thuhina.water.billing.dto.BillingDtos.AccountRow;
import lk.thuhina.water.billing.service.AgingService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseRow;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummary;
import lk.thuhina.water.expenses.dto.ExpenseDtos.ExpenseSummaryRow;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.expenses.service.ExpenseQueryService;
import lk.thuhina.water.reports.dto.ReportDtos.ChartPoint;
import lk.thuhina.water.reports.dto.ReportDtos.ReportKpi;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import lk.thuhina.water.purchasing.service.SupplierPaymentService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Purchasing, Outstanding & aging, Profit and Expenses reports (BRD section 8, prototype reports.html) – the reports the
 * Accountant sees as well (with Sales).
 */
@Component
class FinanceReports {

    /** All accounts in one page for the Outstanding report. */
    private static final int ALL = 100_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final SupplierPaymentService suppliers;
    private final AgingService aging;
    private final ExpenseQueryService expenses;

    FinanceReports(NamedParameterJdbcTemplate jdbc, SupplierPaymentService suppliers, AgingService aging, ExpenseQueryService expenses) {
        this.jdbc = jdbc;
        this.suppliers = suppliers;
        this.aging = aging;
        this.expenses = expenses;
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("f", from).addValue("t", to);
    }

    /** "Empty 20L Bottle" or the product name. */
    private static final String ITEM_NAME = "case when l.item_type = 'BOTTLE' then 'Empty ' || bt.name else pr.name end";

    static String poStatus(String status) {
        return switch (status) {
            case "DRAFT" -> "Draft";
            case "APPROVED" -> "Approved";
            case "SENT" -> "Sent";
            case "PARTLY_RECEIVED" -> "Partly received";
            case "RECEIVED" -> "Received";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }

    // ------------------------------------------------------------------ Purchasing

    ReportView purchasing(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to);
        Map<String, Object> k = jdbc.queryForMap("select count(*) as pos, coalesce(sum(case when po.status <> 'CANCELLED' then "
                + "(select coalesce(sum(l.qty * l.unit_price), 0) from po_line l where l.po_id = po.id) end), 0) as value "
                + "from purchase_order po where po.po_date between :f and :t", p);
        BigDecimal received = jdbc.queryForObject("select coalesce(sum(value), 0) from goods_receipt where received_date between :f and :t",
                p, BigDecimal.class);
        BigDecimal owed = suppliers.owedBySupplier().values().stream().reduce(Money.ZERO, BigDecimal::add);
        ReportBuilder b = new ReportBuilder("purchasing", "Purchasing", from, to).number("Purchase orders", ((Number) k.get("pos")).longValue())
                .money("PO value", (BigDecimal) k.get("value")).money("Goods received", received).money("Owed to suppliers", owed);

        ReportBuilder.Table lines = b.table("Purchase orders – ordered vs received").col("PO", TEXT).col("Date", DATE).col("Supplier", TEXT)
                .col("Item", TEXT).col("Ordered", NUMBER).col("Received", NUMBER).col("Damaged", NUMBER).col("Value", MONEY).col("Status", TEXT);
        jdbc.query("select po.po_no, po.po_date, s.name as supplier, " + ITEM_NAME + " as item, l.qty, l.received_qty, l.damaged_qty, "
                + "l.qty * l.unit_price as value, po.status from purchase_order po join po_line l on l.po_id = po.id "
                + "join supplier s on s.id = po.supplier_id left join bottle_type bt on l.item_type = 'BOTTLE' and bt.code = l.item_code "
                + "left join product pr on l.item_type = 'PRODUCT' and pr.code = l.item_code "
                + "where po.po_date between :f and :t order by po.po_date, po.id, l.line_no", p, rs -> {
                    int damaged = rs.getInt("damaged_qty");
                    lines.row(rs.getString("po_no"), rs.getObject("po_date", LocalDate.class), rs.getString("supplier"), rs.getString("item"),
                            rs.getInt("qty"), rs.getInt("received_qty"), damaged == 0 ? "" : damaged, rs.getBigDecimal("value"),
                            poStatus(rs.getString("status")));
                });
        lines.done();

        ReportBuilder.Table quotes = b.table("Quotations by request and supplier").col("Request", TEXT).col("Date", DATE).col("Supplier", TEXT)
                .col("Total", MONEY).col("Delivery (days)", NUMBER).col("Chosen", TEXT);
        jdbc.query("select r.qr_no, r.request_date, s.name as supplier, q.delivery_days, q.selected, q.selection_reason, "
                + "(select coalesce(sum(ql.unit_price * i.qty), 0) from quotation_line ql join qr_item i on i.qr_id = r.id "
                + "and i.item_code = ql.item_code where ql.quotation_id = q.id and ql.unit_price is not null) as total "
                + "from quotation_request r join quotation q on q.qr_id = r.id join supplier s on s.id = q.supplier_id "
                + "where r.request_date between :f and :t order by r.request_date, r.id, total", p, rs -> {
                    quotes.row(rs.getString("qr_no"), rs.getObject("request_date", LocalDate.class), rs.getString("supplier"),
                            rs.getBigDecimal("total"), rs.getInt("delivery_days"),
                            rs.getBoolean("selected") ? "Yes – " + rs.getString("selection_reason") : "");
                });
        quotes.done();
        return b.build();
    }

    // ------------------------------------------------------------------ Outstanding & aging

    /**
     * Money owed by age, and advances (credit balances) in their own column – so each row adds up: 0–30 + 31–60 + 61–90 +
     * 90+ = Owed, and the total owed is the same as on Billing &amp; Payments.
     */
    ReportView outstanding(LocalDate from, LocalDate to) {
        List<AccountRow> rows = aging.accounts(new AgingService.Filter(null, null, null), to, 0, ALL).items();
        BigDecimal total = sum(rows, FinanceReports::owed);
        BigDecimal advances = sum(rows, FinanceReports::advance);
        BigDecimal over90 = rows.stream().map(AccountRow::over90).reduce(Money.ZERO, BigDecimal::add);
        ReportBuilder b = new ReportBuilder("outstanding", "Outstanding and aging", from, to);
        b.kpi(new ReportKpi("Total outstanding at " + BusinessDates.format(to), ReportBuilder.text(total, MONEY), MONEY, false));
        b.kpi(new ReportKpi("Over 90 days", ReportBuilder.text(over90, MONEY), MONEY, over90.signum() > 0));
        if (advances.signum() != 0) {
            b.kpi(new ReportKpi("Paid in advance", ReportBuilder.text(advances, MONEY), MONEY, false));
        }

        ReportBuilder.Table byType = b.table("By payment type").col("Payment type", TEXT).col("0–30", MONEY).col("31–60", MONEY)
                .col("61–90", MONEY).col("90+", MONEY).col("Owed", MONEY).col("Paid in advance", MONEY);
        for (PayType type : PayType.values()) {
            List<AccountRow> r = rows.stream().filter(a -> type.name().equals(a.payType())).toList();
            byType.row(type.label(), sum(r, AccountRow::days0to30), sum(r, AccountRow::days31to60), sum(r, AccountRow::days61to90),
                    sum(r, AccountRow::over90), sum(r, FinanceReports::owed), sum(r, FinanceReports::advance));
        }
        byType.footer("Total", sum(rows, AccountRow::days0to30), sum(rows, AccountRow::days31to60), sum(rows, AccountRow::days61to90),
                over90, total, advances).done();
        ReportBuilder.Table byCustomer = b.table("By customer").col("Customer", TEXT).col("Pays", TEXT).col("0–30", MONEY).col("31–60", MONEY)
                .col("61–90", MONEY).col("90+", MONEY).col("Owed", MONEY).col("Paid in advance", MONEY);
        rows.forEach(a -> byCustomer.row(a.name() + " (" + a.code() + ")", a.payTypeLabel(), a.days0to30(), a.days31to60(), a.days61to90(),
                a.over90(), owed(a), advance(a)));
        byCustomer.done();
        return b.build();
    }

    /** The money the customer owes (0 when they paid in advance). */
    private static BigDecimal owed(AccountRow a) {
        return a.balance().signum() > 0 ? a.balance() : Money.ZERO;
    }

    /** A credit balance (paid in advance) as a positive amount, else 0. */
    private static BigDecimal advance(AccountRow a) {
        return a.balance().signum() < 0 ? a.balance().negate() : Money.ZERO;
    }

    private static BigDecimal sum(List<AccountRow> rows, java.util.function.Function<AccountRow, BigDecimal> f) {
        return rows.stream().map(f).reduce(Money.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ Profit

    private static final class ProfitRow {
        final String name;
        BigDecimal revenue = Money.ZERO;
        BigDecimal filling = Money.ZERO;
        BigDecimal bottles = Money.ZERO;
        BigDecimal productCost = Money.ZERO;

        ProfitRow(String name) {
            this.name = name;
        }

        BigDecimal profit() {
            return revenue.subtract(filling).subtract(bottles).subtract(productCost);
        }
    }

    ReportView profit(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = range(from, to);
        Map<String, ProfitRow> rows = new LinkedHashMap<>();
        jdbc.query("select code, name from bottle_type where active order by litres desc", Map.of(), rs -> {
            rows.put(rs.getString("code"), new ProfitRow(rs.getString("name")));
        });
        // Revenue per bottle type: water and deposits on the bill lines (reversals are negative).
        jdbc.query("select l.bottle_type_code as code, bt.name, sum(l.line_amount) as revenue from sales_txn t join sales_txn_line l on l.txn_id = t.id "
                + "join bottle_type bt on bt.code = l.bottle_type_code where t.txn_date between :f and :t and t.kind <> 'OPENING_BALANCE' "
                + "and l.bottle_type_code is not null group by l.bottle_type_code, bt.name", p, rs -> {
                    rows.computeIfAbsent(rs.getString("code"), c -> rowNamed(rs)).revenue = Money.of(rs.getBigDecimal("revenue"));
                });
        // Filling cost of the bottles returned filled in the range.
        jdbc.query("select b.bottle_type_code as code, bt.name, sum(r.cost) as cost from factory_return r join factory_batch b on b.id = r.batch_id "
                + "join bottle_type bt on bt.code = b.bottle_type_code where r.return_date between :f and :t group by b.bottle_type_code, bt.name",
                p, rs -> {
                    rows.computeIfAbsent(rs.getString("code"), c -> rowNamed(rs)).filling = Money.of(rs.getBigDecimal("cost"));
                });
        // New bottles bought: goods received (good ones) in the range.
        jdbc.query("select l.item_code as code, bt.name, sum((l.received - l.damaged) * l.unit_price) as cost from goods_receipt g "
                + "join grn_line l on l.grn_id = g.id join bottle_type bt on bt.code = l.item_code where l.item_type = 'BOTTLE' "
                + "and g.received_date between :f and :t group by l.item_code, bt.name", p, rs -> {
                    rows.computeIfAbsent(rs.getString("code"), c -> rowNamed(rs)).bottles = Money.of(rs.getBigDecimal("cost"));
                });
        ProfitRow products = new ProfitRow("Other products");
        Map<String, Object> pr = jdbc.queryForMap("select coalesce(sum(product_amount), 0) as revenue, coalesce(sum(product_cost), 0) as cost "
                + "from sales_txn where txn_date between :f and :t and kind <> 'OPENING_BALANCE'", p);
        products.revenue = Money.of((BigDecimal) pr.get("revenue"));
        products.productCost = Money.of((BigDecimal) pr.get("cost"));
        rows.put("PRODUCTS", products);

        BigDecimal revenue = Money.ZERO;
        BigDecimal filling = Money.ZERO;
        BigDecimal bottles = Money.ZERO;
        BigDecimal productCost = Money.ZERO;
        for (ProfitRow r : rows.values()) {
            revenue = revenue.add(r.revenue);
            filling = filling.add(r.filling);
            bottles = bottles.add(r.bottles);
            productCost = productCost.add(r.productCost);
        }
        BigDecimal gross = revenue.subtract(filling).subtract(bottles).subtract(productCost);

        // Running expenses (M15): whole months from the From month to the To month.
        YearMonth mf = YearMonth.from(from);
        YearMonth mt = YearMonth.from(to);
        ExpenseSummary ex = expenses.summary(mf.toString(), mt.toString());
        BigDecimal running = ex.total();
        BigDecimal net = gross.subtract(running);

        ReportBuilder b = new ReportBuilder("profit", "Profit", from, to).money("Revenue", revenue).money("Filling cost", filling)
                .money("Bottle cost", bottles).money("Product cost", productCost).money("Gross profit", gross).money("Running expenses", running);
        b.kpi(new ReportKpi("Net profit", ReportBuilder.text(net, MONEY), MONEY, net.signum() < 0));
        ReportBuilder.Table t = b.table("By bottle type").col("", TEXT).col("Revenue", MONEY).col("Filling", MONEY).col("Bottles bought", MONEY)
                .col("Product cost", MONEY).col("Profit", MONEY);
        rows.values().forEach(r -> t.row(r.name, r.revenue, r.filling.negate(), r.bottles.negate(), r.productCost.negate(), r.profit()));
        t.footer("Total", revenue, filling.negate(), bottles.negate(), productCost.negate(), gross).done();
        String months = mf.equals(mt) ? ExpenseRules.monthLabel(mf) : ExpenseRules.monthLabel(mf) + " – " + ExpenseRules.monthLabel(mt);
        ReportBuilder.Table n = b.table("Net profit (" + months + " expenses)").col("", TEXT).col("Rs.", MONEY);
        n.boldRow("Gross profit", gross);
        for (ExpenseSummaryRow r : ex.rows()) {
            if (r.total().signum() != 0) {
                n.row("– " + r.name(), r.total().negate());
            }
        }
        n.boldRow("Net profit", net).done();
        b.note("Deposits count as revenue (not refundable). Running expenses come from the Expenses page and are counted for whole "
                + "months (the months of the From and To dates and every month between). Staff, transport and overheads not entered "
                + "as expenses are outside the system.");
        return b.build();
    }

    private static ProfitRow rowNamed(java.sql.ResultSet rs) {
        try {
            return new ProfitRow(rs.getString("name"));
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ Expenses

    ReportView expenses(LocalDate from, LocalDate to) {
        YearMonth mf = YearMonth.from(from);
        YearMonth mt = YearMonth.from(to);
        ExpenseSummary s = expenses.summary(mf.toString(), mt.toString());
        List<ExpenseRow> entries = expenses.entries(mf, mt);
        int months = s.months().size();
        ReportBuilder b = new ReportBuilder("expenses", "Expenses", from, to).money("Total expenses", s.total()).number("Months", months)
                .money("Average a month", months == 0 ? Money.ZERO : Money.of(s.total().divide(BigDecimal.valueOf(months), 2,
                        java.math.RoundingMode.HALF_UP)))
                .number("Entries", entries.size());
        List<ChartPoint> points = new ArrayList<>();
        s.months().forEach(m -> points.add(ReportBuilder.point(YearMonth.parse(m.month()).getMonth().getDisplayName(TextStyle.SHORT,
                Locale.ENGLISH), m.total(), m.label() + ": " + Money.format(m.total()))));
        b.chart("By month", points);
        ReportBuilder.Table t = b.table("By type and month").col("Type", TEXT);
        s.months().forEach(m -> t.col(m.label(), MONEY));
        t.col("Total", MONEY);
        for (ExpenseSummaryRow r : s.rows()) {
            if (r.total().signum() == 0) {
                continue;
            }
            List<Object> cells = new ArrayList<>();
            cells.add(r.name());
            cells.addAll(r.amounts());
            cells.add(r.total());
            t.row(cells.toArray());
        }
        List<Object> foot = new ArrayList<>();
        foot.add("Total");
        s.months().forEach(m -> foot.add(m.total()));
        foot.add(s.total());
        t.footer(foot.toArray()).done();
        ReportBuilder.Table all = b.table("All expenses").col("For month", TEXT).col("Paid on", DATE).col("Type", TEXT).col("Description", TEXT)
                .col("Paid to", TEXT).col("Paid by", TEXT).col("Amount", MONEY);
        entries.forEach(e -> all.row(e.forMonthLabel(), e.paidDate(), e.typeName(), e.description(), e.paidTo(),
                e.methodLabel() + (e.reference() == null ? "" : " " + e.reference()), e.amount()));
        all.footer("Total", "", "", "", "", "", s.total()).done();
        b.note("Expenses are counted for whole months (the months of the From and To dates and every month between).");
        return b.build();
    }
}
