package lk.thuhina.water.reports.service;

import static lk.thuhina.water.reports.service.ReportBuilder.DATE;
import static lk.thuhina.water.reports.service.ReportBuilder.MONEY;
import static lk.thuhina.water.reports.service.ReportBuilder.NUMBER;
import static lk.thuhina.water.reports.service.ReportBuilder.TEXT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Production, Stock and Damage & write-off reports (BRD section 8, prototype reports.html). Admin only. */
@Component
class StockReports {

    private final NamedParameterJdbcTemplate jdbc;

    StockReports(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("f", from).addValue("t", to);
    }

    // ------------------------------------------------------------------ Production

    ReportView production(LocalDate from, LocalDate to) {
        ReportBuilder b = new ReportBuilder("production", "Production", from, to);
        ReportBuilder.Table t = b.table("Batches").col("Batch", TEXT).col("Sent", DATE).col("Factory", TEXT).col("Bottle", TEXT)
                .col("Qty sent", NUMBER).col("Filled back", NUMBER).col("Rejected", NUMBER).col("At factory", NUMBER).col("Filling cost", MONEY);
        long[] sum = new long[4];
        BigDecimal[] cost = {Money.ZERO};
        jdbc.query("select b.batch_no, b.dispatch_date, f.name as factory, bt.litres, b.qty_sent, "
                + "coalesce(sum(r.filled_qty), 0) as filled, coalesce(sum(r.rejected_qty), 0) as rejected, coalesce(sum(r.cost), 0) as cost "
                + "from factory_batch b join factory f on f.id = b.factory_id join bottle_type bt on bt.code = b.bottle_type_code "
                + "left join factory_return r on r.batch_id = b.id where b.dispatch_date between :f and :t "
                + "group by b.id, b.batch_no, b.dispatch_date, f.name, bt.litres, b.qty_sent order by b.dispatch_date, b.id", range(from, to), rs -> {
                    int sent = rs.getInt("qty_sent");
                    int filled = rs.getInt("filled");
                    int rejected = rs.getInt("rejected");
                    BigDecimal c = Money.of(rs.getBigDecimal("cost"));
                    t.row(rs.getString("batch_no"), rs.getObject("dispatch_date", LocalDate.class), rs.getString("factory"),
                            LedgerRules.bottleLabel(rs.getBigDecimal("litres")), sent, filled, rejected, sent - filled - rejected, c);
                    sum[0]++;
                    sum[1] += sent;
                    sum[2] += filled;
                    sum[3] += rejected;
                    cost[0] = cost[0].add(c);
                });
        t.footer("Total", "", "", "", sum[1], sum[2], sum[3], sum[1] - sum[2] - sum[3], cost[0]).done();
        b.number("Batches", sum[0]).number("Sent", sum[1]).number("Filled back", sum[2]).number("Rejected", sum[3])
                .money("Filling cost", cost[0]);
        return b.build();
    }

    // ------------------------------------------------------------------ Stock

    ReportView stock(LocalDate from, LocalDate to) {
        ReportBuilder b = new ReportBuilder("stock", "Stock", from, to);
        ReportBuilder.Table t = b.table("Bottle stock at " + BusinessDates.format(to)).col("Bottle", TEXT).col("Empty in store", NUMBER)
                .col("Filled in store", NUMBER).col("At factory", NUMBER).col("With customers", NUMBER).col("Written off", NUMBER)
                .col("In circulation", NUMBER);
        // Stock on the end date = stock now minus the movements after it.
        jdbc.query("select bt.code, bt.name, "
                + "max(case when s.bucket = 'EMPTY' then s.qty end) - coalesce((select sum(m.qty_delta) from stock_movement m where m.item_code = bt.code and m.bucket = 'EMPTY' and m.movement_date > :t), 0) as empty, "
                + "max(case when s.bucket = 'FILLED' then s.qty end) - coalesce((select sum(m.qty_delta) from stock_movement m where m.item_code = bt.code and m.bucket = 'FILLED' and m.movement_date > :t), 0) as filled, "
                + "max(case when s.bucket = 'FACTORY' then s.qty end) - coalesce((select sum(m.qty_delta) from stock_movement m where m.item_code = bt.code and m.bucket = 'FACTORY' and m.movement_date > :t), 0) as factory, "
                + "max(case when s.bucket = 'CUSTOMERS' then s.qty end) - coalesce((select sum(m.qty_delta) from stock_movement m where m.item_code = bt.code and m.bucket = 'CUSTOMERS' and m.movement_date > :t), 0) as customers, "
                + "max(case when s.bucket = 'WRITTEN_OFF' then s.qty end) - coalesce((select sum(m.qty_delta) from stock_movement m where m.item_code = bt.code and m.bucket = 'WRITTEN_OFF' and m.movement_date > :t), 0) as written_off "
                + "from bottle_type bt join stock_balance s on s.bottle_type_code = bt.code where bt.active "
                + "group by bt.code, bt.name, bt.litres order by bt.litres desc", range(from, to), rs -> {
                    int e = rs.getInt("empty");
                    int f = rs.getInt("filled");
                    int fa = rs.getInt("factory");
                    int c = rs.getInt("customers");
                    t.row(rs.getString("name"), e, f, fa, c, rs.getInt("written_off"), e + f + fa + c);
                });
        t.done();
        ReportBuilder.Table p = b.table("Product stock (now)").col("Product", TEXT).col("In stock", NUMBER).col("Cost value", MONEY);
        BigDecimal[] value = {Money.ZERO};
        jdbc.query("select name, stock_qty, coalesce(cost_price, 0) * stock_qty as value from product where active order by code", Map.of(), rs -> {
            BigDecimal v = Money.of(rs.getBigDecimal("value"));
            value[0] = value[0].add(v);
            p.row(rs.getString("name"), rs.getInt("stock_qty"), v);
        });
        p.footer("Total", "", value[0]).done();
        return b.build();
    }

    // ------------------------------------------------------------------ Damage & write-off

    static String location(String location) {
        if (location == null) {
            return "At customer";
        }
        return switch (location) {
            case "EMPTY_IN_STORE", "EMPTY" -> "Empty in store";
            case "FILLED_IN_STORE", "FILLED" -> "Filled in store";
            case "AT_FACTORY", "FACTORY" -> "At factory";
            case "DURING_DELIVERY" -> "During delivery";
            case "AT_CUSTOMER", "CUSTOMERS" -> "At customer";
            default -> location;
        };
    }

    ReportView damage(LocalDate from, LocalDate to) {
        ReportBuilder b = new ReportBuilder("damage", "Damage and write-off", from, to);
        ReportBuilder.Table t = b.table("Damaged and lost bottles").col("Date", DATE).col("Bottle", TEXT).col("Qty", NUMBER)
                .col("Responsibility", TEXT).col("Where", TEXT).col("Reason", TEXT);
        long[] q = new long[2];
        jdbc.query("select d.damage_date as dt, bt.litres, d.qty, d.responsibility, d.location as loc, d.reason, false as lost "
                + "from damage_record d join bottle_type bt on bt.code = d.bottle_type_code where not d.reversed and d.damage_date between :f and :t "
                + "union all select a.adj_date, bt.litres, -a.delta, 'COMPANY', a.bucket, a.reason, true from stock_adjustment a "
                + "join bottle_type bt on bt.code = a.item_code where a.mode = 'LOST' and a.adj_date between :f and :t order by 1, 2 desc",
                range(from, to), rs -> {
                    boolean company = "COMPANY".equals(rs.getString("responsibility"));
                    int qty = rs.getInt("qty");
                    q[company ? 0 : 1] += qty;
                    t.row(rs.getObject("dt", LocalDate.class), LedgerRules.bottleLabel(rs.getBigDecimal("litres")), qty,
                            company ? "Company" : "Customer", (rs.getBoolean("lost") ? "Lost – " : "") + location(rs.getString("loc")),
                            rs.getString("reason"));
                });
        t.footer("Total", "", q[0] + q[1], "", "", "").done();
        b.number("Written off", q[0] + q[1]).number("Company", q[0]).number("Customer", q[1]);
        return b.build();
    }
}
