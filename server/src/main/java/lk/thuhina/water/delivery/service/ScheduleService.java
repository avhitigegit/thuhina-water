package lk.thuhina.water.delivery.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCounts;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.dto.DeliveryDtos.EnteredBill;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.masterdata.service.PricingService.WaterPrice;
import lk.thuhina.water.sales.model.SalesKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schedule Service (design 5.4, FR-42): who is due on a date. A customer is due when active, registered by then, the date
 * is their delivery day and a whole number of cycles from their next delivery date; for today, customers whose next
 * delivery date has passed (missed) are listed too. The list is read with a fixed number of queries (no query per
 * customer), so a day of 500 customers stays fast (NFR-04).
 */
@Service
public class ScheduleService {

    /** Bills that count as "entered" for the day (design 5.4). */
    static final List<String> DELIVERY_KINDS = List.of(SalesKind.EXCHANGE.name(), SalesKind.LEFT_AT_DOOR.name(),
            SalesKind.FIRST_PURCHASE.name(), SalesKind.EXTRA_ORDER.name());

    private static final int CHUNK = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final PricingService pricing;
    private final Clock clock;

    public ScheduleService(NamedParameterJdbcTemplate jdbc, PricingService pricing, Clock clock) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.clock = clock;
    }

    private record Row(long id, String code, String name, String phone, long areaId, String areaName, PayType payType,
                       BigDecimal creditLimit, BigDecimal balance, int cycleWeeks, LocalDate next, LocalDate registeredOn,
                       DayOfWeek day, long customerTypeId) {
    }

    private record Bottle(String code, String label) {
    }

    private record Have(int held, int toCollect, int owed) {
    }

    /** The due list for {@code date} (default today), optionally for one area. Future dates are allowed (planning). */
    @Transactional(readOnly = true)
    public DueList dueList(LocalDate date, Long areaId) {
        LocalDate today = BusinessDates.today(clock);
        LocalDate d = date == null ? today : date;
        boolean isToday = d.equals(today);

        MapSqlParameterSource p = new MapSqlParameterSource("d", d).addValue("dow", d.getDayOfWeek().getValue());
        String sql = "select c.id, c.code, c.name, c.phone, c.area_id, a.name as area_name, c.pay_type, c.credit_limit, "
                + "coalesce(acc.balance, 0) as balance, c.cycle_weeks, c.next_delivery_date, c.registered_on, c.delivery_day, "
                + "c.customer_type_id from customer c join area a on a.id = c.area_id "
                + "left join customer_account acc on acc.customer_id = c.id where c.status = 'ACTIVE' and ("
                + "(c.delivery_day = :dow and c.registered_on <= :d and mod(:d - c.next_delivery_date, 7 * c.cycle_weeks) = 0)"
                + (isToday ? " or c.next_delivery_date < :d" : "") + ")";
        if (areaId != null) {
            sql += " and c.area_id = :area";
            p.addValue("area", areaId);
        }
        sql += " order by a.name, c.name, c.id";
        List<Row> rows = jdbc.query(sql, p, (rs, i) -> new Row(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("phone"), rs.getLong("area_id"), rs.getString("area_name"), PayType.valueOf(rs.getString("pay_type")),
                rs.getBigDecimal("credit_limit"), rs.getBigDecimal("balance"), rs.getInt("cycle_weeks"),
                rs.getObject("next_delivery_date", LocalDate.class), rs.getObject("registered_on", LocalDate.class),
                DayOfWeek.of(rs.getInt("delivery_day")), rs.getLong("customer_type_id")));

        List<Bottle> bottles = jdbc.query("select code, litres from bottle_type where active order by litres desc, code",
                Map.of(), (rs, i) -> new Bottle(rs.getString("code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres"))));
        List<Long> ids = rows.stream().map(Row::id).toList();
        Map<Long, Map<String, Integer>> usual = new HashMap<>();
        Map<Long, Map<String, Have>> have = new HashMap<>();
        Map<Long, List<EnteredBill>> entered = new HashMap<>();
        for (int i = 0; i < ids.size(); i += CHUNK) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + CHUNK));
            MapSqlParameterSource q = new MapSqlParameterSource("ids", chunk).addValue("d", d).addValue("kinds", DELIVERY_KINDS);
            jdbc.query("select customer_id, bottle_type_code, qty from customer_usual_qty where customer_id in (:ids)", q, rs -> {
                usual.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getString(2), rs.getInt(3));
            });
            jdbc.query("select customer_id, bottle_type_code, held, to_collect, owed from customer_bottle_balance "
                    + "where customer_id in (:ids)", q, rs -> {
                have.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getString(2),
                        new Have(rs.getInt(3), rs.getInt(4), rs.getInt(5)));
            });
            jdbc.query("select id, customer_id, bill_no, paper_bill_no, kind from sales_txn where txn_date = :d "
                    + "and customer_id in (:ids) and reversed_by_id is null and kind in (:kinds) order by id", q, rs -> {
                SalesKind kind = SalesKind.valueOf(rs.getString("kind"));
                entered.computeIfAbsent(rs.getLong("customer_id"), k -> new ArrayList<>()).add(new EnteredBill(rs.getLong("id"),
                        rs.getString("bill_no"), rs.getString("paper_bill_no"), kind.name(), kind.label()));
            });
        }
        Map<Long, Long> types = new LinkedHashMap<>();
        rows.forEach(r -> types.put(r.id(), r.customerTypeId()));
        Map<Long, Map<String, WaterPrice>> prices = pricing.waterPrices(types, bottles.stream().map(Bottle::code).toList(), d);

        List<DueCustomer> items = new ArrayList<>();
        int due = 0;
        int listed = 0;
        int done = 0;
        int noBottles = 0;
        int overdue = 0;
        for (Row r : rows) {
            boolean isDue = ScheduleRules.isDue(true, r.registeredOn(), r.day(), r.cycleWeeks(), r.next(), d);
            boolean late = isToday && r.next().isBefore(d);
            List<DueBottle> lines = new ArrayList<>();
            int heldTotal = 0;
            for (Bottle b : bottles) {
                int u = usual.getOrDefault(r.id(), Map.of()).getOrDefault(b.code(), 0);
                Have h = have.getOrDefault(r.id(), Map.of()).getOrDefault(b.code(), new Have(0, 0, 0));
                heldTotal += h.held();
                WaterPrice wp = prices.getOrDefault(r.id(), Map.of()).get(b.code());
                lines.add(new DueBottle(b.code(), b.label(), u, h.held(), h.toCollect(), h.owed(), u, u + h.toCollect(),
                        wp == null ? null : wp.price(), wp != null && wp.agreed(), wp == null ? null : wp.standard()));
            }
            List<EnteredBill> bills = entered.getOrDefault(r.id(), List.of());
            boolean isEntered = !bills.isEmpty();
            boolean none = heldTotal == 0;
            boolean inGrid = !isEntered && !none;
            due++;
            listed += inGrid ? 1 : 0;
            done += isEntered ? 1 : 0;
            noBottles += !isEntered && none ? 1 : 0;
            overdue += late ? 1 : 0;
            items.add(new DueCustomer(r.id(), r.code(), r.name(), r.phone(), r.areaId(), r.areaName(), r.payType().name(),
                    r.payType().label(), r.creditLimit(), r.balance(), r.cycleWeeks(), r.next(),
                    ScheduleRules.advance(r.next(), r.cycleWeeks(), d), isDue, late,
                    late ? (int) ChronoUnit.DAYS.between(r.next(), d) : 0, isEntered, none, inGrid, bills, lines));
        }
        return new DueList(d, d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH), isToday,
                new DueCounts(due, listed, done, noBottles, overdue), items);
    }
}
