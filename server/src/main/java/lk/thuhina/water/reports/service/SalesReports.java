package lk.thuhina.water.reports.service;

import static lk.thuhina.water.reports.service.ReportBuilder.DATE;
import static lk.thuhina.water.reports.service.ReportBuilder.MONEY;
import static lk.thuhina.water.reports.service.ReportBuilder.NUMBER;
import static lk.thuhina.water.reports.service.ReportBuilder.TEXT;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.delivery.service.ScheduleService.BottleInfo;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.inventory.dto.StockDtos.BottleStock;
import lk.thuhina.water.inventory.service.StockQueryService;
import lk.thuhina.water.reports.dto.ReportDtos.ChartPoint;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sales, Bottle movement and Delivery reports (BRD section 8, prototype reports.html). A reversal counts on its own date
 * against the kind of the bill it reverses; opening balances and "collect empties" are not sales.
 */
@Component
class SalesReports {

    /** Days shown one by one in the Sales chart; longer ranges are shown by month. */
    static final int CHART_DAYS = 92;

    /** Bills in the range with their sign and the kind of the bill (the original kind for a reversal). */
    private static final String TX = "with tx as (select t.id, t.txn_date, t.amount, t.water_amount as w, t.deposit_amount as d, "
            + "t.product_amount as p, t.customer_id, t.walk_in_name, case when t.kind = 'REVERSAL' then o.kind else t.kind end as k, "
            + "case when t.kind = 'REVERSAL' then 0 else 1 end as bill "
            + "from sales_txn t left join sales_txn o on o.id = t.reversal_of_id where t.txn_date between :f and :t "
            + "and t.kind not in ('OPENING_BALANCE', 'COLLECT_EMPTIES') "
            + "and coalesce(o.kind, '') not in ('OPENING_BALANCE', 'COLLECT_EMPTIES')) ";

    private final NamedParameterJdbcTemplate jdbc;
    private final ScheduleService schedule;
    private final StockQueryService stock;

    SalesReports(NamedParameterJdbcTemplate jdbc, ScheduleService schedule, StockQueryService stock) {
        this.jdbc = jdbc;
        this.schedule = schedule;
        this.stock = stock;
    }

    static String saleType(String kind) {
        return switch (kind) {
            case "FIRST_PURCHASE" -> "First purchase";
            case "EXCHANGE", "LEFT_AT_DOOR" -> "Exchange";
            case "EXTRA_ORDER" -> "Extra order";
            case "PRODUCT_SALE" -> "Product";
            case "CUSTOMER_DAMAGE" -> "Bottle replacement";
            case "OWED_SETTLEMENT" -> "Bottles owed (deposit)";
            default -> kind;
        };
    }

    private record Group(String key, long bills, BigDecimal water, BigDecimal deposits, BigDecimal products) {

        BigDecimal total() {
            return water.add(deposits).add(products);
        }
    }

    private List<Group> groups(String keySql, String join, MapSqlParameterSource p, String order) {
        return jdbc.query(TX + "select " + keySql + " as g, sum(bill) as n, coalesce(sum(w), 0) as w, coalesce(sum(d), 0) as d, "
                        + "coalesce(sum(p), 0) as p from tx " + join + " group by 1 order by " + order, p,
                (rs, i) -> new Group(rs.getString("g"), rs.getLong("n"), Money.of(rs.getBigDecimal("w")), Money.of(rs.getBigDecimal("d")),
                        Money.of(rs.getBigDecimal("p"))));
    }

    private static void groupTable(ReportBuilder b, String title, String header, List<Group> rows) {
        ReportBuilder.Table t = b.table(title).col(header, TEXT).col("Bills", NUMBER).col("Water", MONEY).col("Deposits", MONEY)
                .col("Products", MONEY).col("Total", MONEY);
        rows.forEach(g -> t.row(g.key(), g.bills(), g.water(), g.deposits(), g.products(), g.total()));
        t.done();
    }

    // ------------------------------------------------------------------ Sales

    ReportView sales(LocalDate from, LocalDate to) {
        MapSqlParameterSource p = new MapSqlParameterSource("f", from).addValue("t", to);
        List<Group> byKind = new ArrayList<>();
        Map<String, Group> merged = new LinkedHashMap<>();
        for (Group g : groups("k", "", p, "1")) {
            String label = saleType(g.key());
            Group m = merged.get(label);
            merged.put(label, m == null ? new Group(label, g.bills(), g.water(), g.deposits(), g.products())
                    : new Group(label, m.bills() + g.bills(), m.water().add(g.water()), m.deposits().add(g.deposits()),
                    m.products().add(g.products())));
        }
        byKind.addAll(merged.values());
        byKind.sort((a, c) -> c.total().compareTo(a.total()));
        BigDecimal water = byKind.stream().map(Group::water).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal deposits = byKind.stream().map(Group::deposits).reduce(Money.ZERO, BigDecimal::add);
        BigDecimal products = byKind.stream().map(Group::products).reduce(Money.ZERO, BigDecimal::add);
        long bills = byKind.stream().mapToLong(Group::bills).sum();

        // Agreed prices (FR-05): water billed vs the standard price of the customer's type on the bill date.
        record Agreed(String name, String type, long bottles, BigDecimal billed, BigDecimal standard) {
        }
        List<Agreed> agreed = jdbc.query("select c.name, ct.name as type_name, sum(sg * l.filled) as bottles, "
                        + "sum(sg * l.filled * l.unit_price) as billed, sum(sg * l.filled * std.price) as std "
                        + "from (select t.id, coalesce(o.txn_date, t.txn_date) as bill_date, t.customer_id, "
                        + "case when t.kind = 'REVERSAL' then -1 else 1 end as sg from sales_txn t left join sales_txn o on o.id = t.reversal_of_id "
                        + "where t.txn_date between :f and :t and t.kind not in ('OPENING_BALANCE', 'COLLECT_EMPTIES') and t.customer_id is not null) x "
                        + "join sales_txn_line l on l.txn_id = x.id join customer c on c.id = x.customer_id "
                        + "join customer_type ct on ct.id = c.customer_type_id "
                        + "cross join lateral (select e.price from price_entry e where e.kind = 'WATER' and e.bottle_type_code = l.bottle_type_code "
                        + "and e.customer_type_id = c.customer_type_id and e.effective_from <= x.bill_date order by e.effective_from desc limit 1) std "
                        + "where l.bottle_type_code is not null and l.filled <> 0 and l.unit_price is not null and std.price <> l.unit_price "
                        + "group by c.id, c.name, ct.name order by sum(sg * l.filled * (std.price - l.unit_price)) desc, c.name", p,
                (rs, i) -> new Agreed(rs.getString("name"), rs.getString("type_name"), rs.getLong("bottles"), Money.of(rs.getBigDecimal("billed")),
                        Money.of(rs.getBigDecimal("std"))));
        BigDecimal below = agreed.stream().map(a -> a.standard().subtract(a.billed())).reduce(Money.ZERO, BigDecimal::add);

        ReportBuilder b = new ReportBuilder("sales", "Sales", from, to)
                .money("Total sales", water.add(deposits).add(products)).money("Water", water).money("Deposits", deposits)
                .money("Products", products).number("Bills", bills).money("Below standard (agreed prices)", below);
        b.chart(ChronoUnit.DAYS.between(from, to) < CHART_DAYS ? "By day" : "By month", salesChart(from, to, p));
        groupTable(b, "By sale type", "Sale type", byKind);
        groupTable(b, "By customer type", "Customer type", groups("coalesce(ct.name, 'Walk-in')",
                "left join customer c on c.id = tx.customer_id left join customer_type ct on ct.id = c.customer_type_id", p,
                "sum(w + d + p) desc"));
        List<Group> byMonth = groups("to_char(txn_date, 'YYYY-MM')", "", p, "1").stream()
                .map(g -> new Group(ExpenseRules.monthLabel(YearMonth.parse(g.key())), g.bills(), g.water(), g.deposits(), g.products()))
                .toList();
        groupTable(b, "By month", "Month", byMonth);
        groupTable(b, "By customer (top 15)", "Customer", groups("coalesce(c.name, 'Walk-in')", "left join customer c on c.id = tx.customer_id",
                p, "sum(w + d + p) desc limit 15"));
        ReportBuilder.Table t = b.table("Customers on agreed prices – water billed vs standard price").col("Customer", TEXT)
                .col("Customer type", TEXT).col("Bottles", NUMBER).col("Billed", MONEY).col("At standard price", MONEY).col("Difference", MONEY);
        agreed.forEach(a -> t.row(a.name(), a.type(), a.bottles(), a.billed(), a.standard(), a.billed().subtract(a.standard())));
        t.done();
        return b.build();
    }

    private List<ChartPoint> salesChart(LocalDate from, LocalDate to, MapSqlParameterSource p) {
        Map<LocalDate, BigDecimal> byDay = new HashMap<>();
        jdbc.query("select txn_date, sum(amount) as total from sales_txn where txn_date between :f and :t and kind <> 'OPENING_BALANCE' "
                + "group by txn_date", p, rs -> {
                    byDay.put(rs.getObject("txn_date", LocalDate.class), Money.of(rs.getBigDecimal("total")));
                });
        List<ChartPoint> out = new ArrayList<>();
        if (ChronoUnit.DAYS.between(from, to) < CHART_DAYS) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                BigDecimal v = byDay.getOrDefault(d, Money.ZERO);
                out.add(ReportBuilder.point(String.format("%02d", d.getDayOfMonth()), v, BusinessDates.format(d) + ": " + Money.format(v)));
            }
            return out;
        }
        Map<YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            byMonth.put(m, Money.ZERO);
        }
        byDay.forEach((d, v) -> byMonth.merge(YearMonth.from(d), v, BigDecimal::add));
        byMonth.forEach((m, v) -> out.add(ReportBuilder.point(m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), v,
                ExpenseRules.monthLabel(m) + ": " + Money.format(v))));
        return out;
    }

    // ------------------------------------------------------------------ Bottle movement

    ReportView movement(LocalDate from, LocalDate to) {
        List<BottleStock> bottles = stock.overview().bottles();
        BottleStock main = bottles.isEmpty() ? null : bottles.getFirst();
        String label = main == null ? "20L" : main.label();
        ReportBuilder b = new ReportBuilder("movement", "Bottle movement", from, to);
        if (main != null) {
            b.number(label + " in circulation", main.inCirculation()).number("With customers", main.customers())
                    .number("Empties to collect now", main.customersToCollect()).number("Bottles owed now", main.customersOwed());
        }
        Map<String, Object> f = main == null ? Map.of() : jdbc.queryForMap("select coalesce(sum(sg * l.filled), 0) as delivered, "
                        + "coalesce(sum(sg * l.empties), 0) as collected, "
                        + "coalesce(sum(sg * case when k = 'FIRST_PURCHASE' then l.filled when k = 'EXTRA_ORDER' then l.new_bottles else 0 end), 0) as new_bottles, "
                        + "coalesce(sum(sg * l.old_bottles), 0) as old_in, coalesce(sum(sg * l.to_collect_added), 0) as left_door, "
                        + "coalesce(sum(sg * l.owed_added), 0) as owed "
                        + "from (select t.id, case when t.kind = 'REVERSAL' then -1 else 1 end as sg, "
                        + "case when t.kind = 'REVERSAL' then o.kind else t.kind end as k from sales_txn t "
                        + "left join sales_txn o on o.id = t.reversal_of_id where t.txn_date between :f and :t "
                        + "and t.kind <> 'OPENING_BALANCE') x join sales_txn_line l on l.txn_id = x.id where l.bottle_type_code = :b",
                new MapSqlParameterSource("f", from).addValue("t", to).addValue("b", main.code()));
        ReportBuilder.Table flows = b.table(label + " bottle flows " + BusinessDates.format(from) + " – " + BusinessDates.format(to))
                .col("Flow", TEXT).col("Bottles", NUMBER);
        if (main != null) {
            flows.row("Filled bottles delivered", f.get("delivered")).row("Empties collected", f.get("collected"))
                    .row("New bottles to customers", f.get("new_bottles")).row("Old bottles of other brands taken in", f.get("old_in"))
                    .row("Left at door (empties to collect)", f.get("left_door")).row("New bottles owed", f.get("owed"));
        }
        flows.done();
        ReportBuilder.Table c = b.table("Customers with empties to collect or bottles owed").col("Customer", TEXT).col("Area", TEXT)
                .col("To collect", NUMBER).col("Owed", NUMBER);
        jdbc.query("select c.code, c.name, a.name as area, sum(bb.to_collect) as tc, sum(bb.owed) as ow from customer_bottle_balance bb "
                + "join customer c on c.id = bb.customer_id join area a on a.id = c.area_id group by c.id, c.code, c.name, a.name "
                + "having sum(bb.to_collect) > 0 or sum(bb.owed) > 0 order by a.name, c.name", Map.of(), rs -> {
                    c.row(rs.getString("name") + " (" + rs.getString("code") + ")", rs.getString("area"), rs.getInt("tc"), rs.getInt("ow"));
                });
        c.done();
        return b.build();
    }

    // ------------------------------------------------------------------ Delivery

    /** Days in the Delivery report (newest first); a longer range shows its last days. */
    static final int DELIVERY_DAYS = 92;

    ReportView delivery(LocalDate from, LocalDate to) {
        LocalDate start = ChronoUnit.DAYS.between(from, to) >= DELIVERY_DAYS ? to.minusDays(DELIVERY_DAYS - 1L) : from;
        List<BottleInfo> bottles = schedule.activeBottles();
        Map<LocalDate, Integer> leftAtDoor = new HashMap<>();
        jdbc.query("select txn_date, count(*) as n from sales_txn where kind = 'LEFT_AT_DOOR' and reversed_by_id is null "
                + "and txn_date between :f and :t group by txn_date", new MapSqlParameterSource("f", start).addValue("t", to), rs -> {
                    leftAtDoor.put(rs.getObject("txn_date", LocalDate.class), rs.getInt("n"));
                });
        record Day(LocalDate date, int due, int entered, int left, String missed) {
        }
        List<Day> days = new ArrayList<>();
        for (LocalDate d = to; !d.isBefore(start); d = d.minusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            List<DueCustomer> due = schedule.customers(d, null, false, false, bottles).stream()
                    .filter(c -> c.due() && !c.noBottles())
                    .toList();
            List<String> missed = due.stream().filter(c -> !c.entered()).map(DueCustomer::name).toList();
            days.add(new Day(d, due.size(), due.size() - missed.size(), leftAtDoor.getOrDefault(d, 0), String.join(", ", missed)));
        }
        ReportBuilder b = new ReportBuilder("delivery", "Delivery", start, to)
                .number("Due", days.stream().mapToInt(Day::due).sum()).number("Entered", days.stream().mapToInt(Day::entered).sum())
                .number("Left at door", days.stream().mapToInt(Day::left).sum());
        ReportBuilder.Table t = b.table("By day").col("Date", DATE).col("Day", TEXT).col("Due", NUMBER).col("Entered", NUMBER)
                .col("Left at door", NUMBER).col("Not entered", TEXT);
        days.forEach(d -> t.row(d.date(), d.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), d.due(), d.entered(),
                d.left(), d.missed()));
        t.done();
        if (!start.equals(from)) {
            b.note("The Delivery report shows at most the last " + DELIVERY_DAYS + " days of the range.");
        }
        return b.build();
    }
}
