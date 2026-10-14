package lk.thuhina.water.reports.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.billing.dto.BillingDtos.AccountSummary;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceMonthRow;
import lk.thuhina.water.billing.service.AgingService;
import lk.thuhina.water.billing.service.BillingQueryService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.expenses.service.ExpenseQueryService;
import lk.thuhina.water.inventory.dto.StockDtos.BottleStock;
import lk.thuhina.water.inventory.dto.StockDtos.StockOverview;
import lk.thuhina.water.inventory.service.StockQueryService;
import lk.thuhina.water.reports.dto.DashboardDtos.AttentionItem;
import lk.thuhina.water.reports.dto.DashboardDtos.DashDelivery;
import lk.thuhina.water.reports.dto.DashboardDtos.DashKpi;
import lk.thuhina.water.reports.dto.DashboardDtos.DashboardView;
import lk.thuhina.water.reports.dto.DashboardDtos.DaySales;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Permissions;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard (design 6, prototype dashboard.html, BRD section 8, FR-31): KPIs for today, low-stock banners, today's deliveries,
 * the "needs attention" list and the sales of the last 14 days, in one call. Each link is given only when the user's role may
 * open the page (the KPI still shows).
 */
@Service
public class DashboardService {

    /** Agreed prices ending within this many days are listed. */
    static final int PRICE_ENDING_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final StockQueryService stock;
    private final ScheduleService schedule;
    private final AgingService aging;
    private final BillingQueryService billing;
    private final ExpenseQueryService expenses;
    private final Clock clock;

    public DashboardService(NamedParameterJdbcTemplate jdbc, StockQueryService stock, ScheduleService schedule, AgingService aging,
                            BillingQueryService billing, ExpenseQueryService expenses, Clock clock) {
        this.jdbc = jdbc;
        this.stock = stock;
        this.schedule = schedule;
        this.aging = aging;
        this.billing = billing;
        this.expenses = expenses;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        LocalDate today = BusinessDates.today(clock);
        Set<String> perms = CurrentUser.get().map(u -> u.permissions()).orElse(Set.of());
        Links links = new Links(perms);
        MapSqlParameterSource p = new MapSqlParameterSource("d", today);

        // Sales and money in today (reversals dated today count against them; opening balances are not sales).
        Map<String, Object> s = jdbc.queryForMap("select coalesce(sum(amount), 0) as sales, coalesce(sum(paid_on_bill), 0) as paid, "
                + "count(*) filter (where kind <> 'REVERSAL') as bills from sales_txn where txn_date = :d and kind <> 'OPENING_BALANCE'", p);
        BigDecimal payments = jdbc.queryForObject("select coalesce(sum(amount), 0) from customer_payment where pay_date = :d", p,
                BigDecimal.class);
        BigDecimal sales = Money.of((BigDecimal) s.get("sales"));
        BigDecimal collected = Money.of(((BigDecimal) s.get("paid")).add(payments));
        long bills = ((Number) s.get("bills")).longValue();

        StockOverview st = stock.overview();
        BottleStock main = st.bottles().isEmpty() ? null : st.bottles().getFirst();
        int atFactory = st.bottles().stream().mapToInt(BottleStock::factory).sum();
        int toCollect = st.bottles().stream().mapToInt(BottleStock::customersToCollect).sum();
        int owed = st.bottles().stream().mapToInt(BottleStock::customersOwed).sum();
        DueList due = schedule.dueList(today, null);
        AccountSummary acc = aging.summary(today);

        List<DashKpi> kpis = new ArrayList<>();
        kpis.add(new DashKpi("sales", "Sales today", Money.format(sales), bills + " bill" + (bills == 1 ? "" : "s"),
                links.of(Permissions.SALES_VIEW, "/sales/bills"), false));
        kpis.add(new DashKpi("collected", "Collected today", Money.format(collected), "cash and payments",
                links.of(Permissions.BILLING_VIEW, "/billing"), false));
        if (main != null) {
            kpis.add(new DashKpi("filled", "Filled " + main.label() + " in store", String.valueOf(main.filled()),
                    "minimum " + (main.minFilled() == null ? 0 : main.minFilled()), links.of(Permissions.STOCK_VIEW, "/inventory"), main.low()));
        }
        kpis.add(new DashKpi("factory", "Bottles at factory", String.valueOf(atFactory), "", links.of(Permissions.PRODUCTION_VIEW, "/production"),
                false));
        kpis.add(new DashKpi("deliveries", "Deliveries today", due.counts().entered() + " of " + due.counts().due(), "entered",
                links.of(Permissions.DELIVERY_LIST_VIEW, "/delivery/daily-list"), false));
        kpis.add(new DashKpi("toCollect", "Empties to collect", String.valueOf(toCollect), "left at the door",
                links.of(Permissions.SALES_VIEW, "/sales/bills"), false));
        kpis.add(new DashKpi("owed", "Bottles owed", String.valueOf(owed), "shortages", links.of(Permissions.SALES_VIEW, "/sales/bills"), false));
        kpis.add(new DashKpi("outstanding", "Outstanding", Money.format(acc.totalOutstanding()), "all customers",
                links.of(Permissions.BILLING_VIEW, "/billing"), false));

        List<DashDelivery> deliveries = due.items().stream().map(DashboardService::delivery).toList();
        return new DashboardView(today, st.alerts(), links.of(Permissions.PRODUCTION_EDIT, "/production"), kpis, deliveries,
                links.of(Permissions.DELIVERY_LIST_VIEW, "/delivery/daily-list"), attention(today, links, acc), sales14(today));
    }

    private static DashDelivery delivery(DueCustomer c) {
        String usual = String.join(" ", c.bottles().stream().filter(b -> b.usual() > 0).map(DashboardService::usual).toList());
        String status = c.entered() ? "ENTERED" : c.overdue() ? "MISSED" : "DUE";
        String label = c.entered() ? "Entered" : c.overdue() ? "Missed " + BusinessDates.format(c.nextDeliveryDate()) : "Due";
        return new DashDelivery(c.id(), c.code(), c.name(), c.areaName(), usual, status, label);
    }

    private static String usual(DueBottle b) {
        return b.usual() + "×" + b.label();
    }

    /** Sales per day for the last 14 days (oldest first), as the prototype's chart. */
    private List<DaySales> sales14(LocalDate today) {
        LocalDate from = today.minusDays(13);
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        jdbc.query("select txn_date, sum(amount) as total from sales_txn where txn_date between :f and :t and kind <> 'OPENING_BALANCE' "
                + "group by txn_date", new MapSqlParameterSource("f", from).addValue("t", today), rs -> {
                    byDay.put(rs.getObject("txn_date", LocalDate.class), Money.of(rs.getBigDecimal("total")));
                });
        List<DaySales> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            out.add(new DaySales(d, byDay.getOrDefault(d, Money.ZERO)));
        }
        return out;
    }

    // ------------------------------------------------------------------ needs attention

    private List<AttentionItem> attention(LocalDate today, Links links, AccountSummary acc) {
        List<AttentionItem> out = new ArrayList<>();
        String purchasing = links.of(Permissions.PURCHASING_VIEW, "/purchasing#quotes");
        String orders = links.of(Permissions.PURCHASING_VIEW, "/purchasing#pos");
        jdbc.query("select qr_no from quotation_request where status = 'QUOTES_RECEIVED' order by id", Map.of(), rs -> {
            out.add(new AttentionItem("QUOTATION_REQUEST", rs.getString("qr_no"), " – quotations waiting for a choice", purchasing));
        });
        jdbc.query("select po_no, status from purchase_order where status in ('DRAFT', 'APPROVED', 'SENT', 'PARTLY_RECEIVED') order by id",
                Map.of(), rs -> {
                    boolean draft = "DRAFT".equals(rs.getString("status"));
                    out.add(new AttentionItem(draft ? "PO_DRAFT" : "PO_RECEIVE", rs.getString("po_no"),
                            draft ? " – draft PO waiting for approval" : " – goods to receive", orders));
                });

        // Customer quotations (M16): not sent, close to expiry, accepted but not registered or prices not applied.
        jdbc.query("select q.id, q.cq_no, q.org_name, q.status, q.valid_until, q.customer_id, q.prices_applied_on, "
                        + "exists (select 1 from cq_line l where l.quotation_id = q.id and l.item_kind = 'WATER') as has_water "
                        + "from customer_quotation q where q.status in ('DRAFT', 'SENT', 'ACCEPTED') order by q.id",
                Map.of(), rs -> {
                    String href = links.of(Permissions.QUOTATIONS_VIEW, "/sales/quotations?id=" + rs.getLong("id"));
                    String no = rs.getString("cq_no");
                    String org = rs.getString("org_name");
                    LocalDate valid = rs.getObject("valid_until", LocalDate.class);
                    switch (rs.getString("status")) {
                        case "DRAFT" -> out.add(new AttentionItem("CQ_DRAFT", no, " – customer quotation to " + org + " not sent yet", href));
                        case "SENT" -> {
                            if (!valid.isBefore(today) && !valid.isAfter(today.plusDays(7))) {
                                out.add(new AttentionItem("CQ_EXPIRING", no, " – " + org + ": no answer yet, valid until "
                                        + BusinessDates.format(valid), href));
                            }
                        }
                        default -> {
                            if (rs.getObject("customer_id") == null) {
                                out.add(new AttentionItem("CQ_REGISTER", no, " – accepted by " + org + ", register as a customer", href));
                            } else if (rs.getObject("prices_applied_on") == null && rs.getBoolean("has_water")) {
                                out.add(new AttentionItem("CQ_PRICES", no, " – accepted by " + org + ", apply the quoted prices", href));
                            }
                        }
                    }
                });

        // Agreed prices in force today that end within 30 days (FR-05).
        jdbc.query("select c.code, c.name, b.litres, p.price, p.valid_until from customer_price p join customer c on c.id = p.customer_id "
                        + "join bottle_type b on b.code = p.bottle_type_code where not p.cancelled and p.valid_from <= :d "
                        + "and p.valid_until between :d and :end order by p.valid_until, c.name",
                new MapSqlParameterSource("d", today).addValue("end", today.plusDays(PRICE_ENDING_DAYS)), rs -> {
                    out.add(new AttentionItem("PRICE_ENDING", rs.getString("name"), " – agreed "
                            + rs.getBigDecimal("litres").stripTrailingZeros().toPlainString() + "L price " + Money.format(rs.getBigDecimal("price"))
                            + " ends " + BusinessDates.format(rs.getObject("valid_until", LocalDate.class)),
                            links.of(Permissions.CUSTOMERS_VIEW, "/master-data/customers?id=" + rs.getString("code"))));
                });

        YearMonth last = YearMonth.from(today).minusMonths(1);
        if (expenses.monthTotal(last).signum() == 0) {
            out.add(new AttentionItem("NO_EXPENSES", "No expenses entered", " for " + ExpenseRules.monthLabel(last),
                    links.of(Permissions.EXPENSES_VIEW, "/expenses?month=" + last)));
        }
        // Monthly invoices for last month still to be generated (customers with something to bill and no invoice).
        long toInvoice = billing.monthRows(last.toString(), null).stream().filter(DashboardService::billable).count();
        if (toInvoice > 0) {
            out.add(new AttentionItem("INVOICES", toInvoice + " monthly invoice" + (toInvoice == 1 ? "" : "s"), " for "
                    + ExpenseRules.monthLabel(last) + " not generated", links.of(Permissions.BILLING_VIEW, "/billing#invoices")));
        }
        if (acc.overLimit() > 0) {
            out.add(new AttentionItem("OVER_LIMIT", acc.overLimit() + " customer" + (acc.overLimit() == 1 ? "" : "s"),
                    " over the credit limit", links.of(Permissions.BILLING_VIEW, "/billing")));
        }
        long aged = aging.over90Count(today);
        if (aged > 0) {
            out.add(new AttentionItem("OVER_90", aged + " customer" + (aged == 1 ? "" : "s"), " owing for more than 90 days",
                    links.of(Permissions.BILLING_VIEW, "/billing")));
        }
        // Factory batches with bottles short: part returned, the rest still at the factory.
        jdbc.query("select b.batch_no, f.name as factory, bt.litres, b.qty_sent - coalesce(sum(r.filled_qty + r.rejected_qty), 0) as short "
                        + "from factory_batch b join factory f on f.id = b.factory_id join bottle_type bt on bt.code = b.bottle_type_code "
                        + "join factory_return r on r.batch_id = b.id group by b.id, b.batch_no, f.name, bt.litres, b.qty_sent "
                        + "having b.qty_sent > coalesce(sum(r.filled_qty + r.rejected_qty), 0) order by b.id",
                Map.of(), rs -> {
                    out.add(new AttentionItem("BATCH_SHORT", rs.getString("batch_no"), " – " + rs.getInt("short") + " × "
                            + rs.getBigDecimal("litres").stripTrailingZeros().toPlainString() + "L still at " + rs.getString("factory"),
                            links.of(Permissions.PRODUCTION_VIEW, "/production")));
                });
        return out;
    }

    private static boolean billable(InvoiceMonthRow r) {
        return r.invoice() == null && "ACTIVE".equals(r.status()) && (r.deliveries() > 0 || r.totalDue().signum() != 0);
    }

    /** A link only when the user's role may open the page. */
    private record Links(Set<String> permissions) {

        String of(String permission, String href) {
            return permissions.contains(permission) ? href : null;
        }
    }
}
