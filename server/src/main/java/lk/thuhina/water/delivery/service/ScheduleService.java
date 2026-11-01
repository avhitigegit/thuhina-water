package lk.thuhina.water.delivery.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.delivery.dto.DeliveryDtos.BottleQty;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCounts;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueTotals;
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

    static final int CHUNK = 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final PricingService pricing;
    private final Clock clock;

    public ScheduleService(NamedParameterJdbcTemplate jdbc, PricingService pricing, Clock clock) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.clock = clock;
    }

    private record Row(long id, String code, String name, String phone, String address, String notes, long areaId, String areaName,
                       PayType payType, BigDecimal creditLimit, BigDecimal balance, int cycleWeeks, LocalDate next,
                       LocalDate registeredOn, DayOfWeek day, long customerTypeId) {
    }

    /** An active bottle type: code, label (20L) and name, largest first. */
    public record BottleInfo(String code, String label, String name) {
    }

    private record Have(int held, int toCollect, int owed) {
    }

    /** Bottles needed on one day (FR-45): per bottle type, the usual quantities and the number of customers taking it. */
    public record DayNeed(LocalDate date, Map<String, Integer> qty, Map<String, Integer> customers) {
    }

    public LocalDate today() {
        return BusinessDates.today(clock);
    }

    @Transactional(readOnly = true)
    public List<BottleInfo> activeBottles() {
        return jdbc.query("select code, name, litres from bottle_type where active order by litres desc, code", Map.of(),
                (rs, i) -> new BottleInfo(rs.getString("code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres")), rs.getString("name")));
    }

    /** The due list for {@code date} (default today), optionally for one area. Future dates are allowed (planning). */
    @Transactional(readOnly = true)
    public DueList dueList(LocalDate date, Long areaId) {
        LocalDate d = date == null ? today() : date;
        List<BottleInfo> bottles = activeBottles();
        List<DueCustomer> items = customers(d, areaId, false, true, bottles);
        int listed = 0;
        int done = 0;
        int noBottles = 0;
        int overdue = 0;
        int toCollect = 0;
        int owed = 0;
        BigDecimal money = Money.ZERO;
        Map<String, Integer> load = new LinkedHashMap<>();
        bottles.forEach(b -> load.put(b.code(), 0));
        for (DueCustomer c : items) {
            listed += c.listed() ? 1 : 0;
            done += c.entered() ? 1 : 0;
            noBottles += !c.entered() && c.noBottles() ? 1 : 0;
            overdue += c.overdue() ? 1 : 0;
            for (DueBottle b : c.bottles()) {
                load.merge(b.bottleTypeCode(), b.usual(), Integer::sum);
                toCollect += b.toCollect();
                owed += b.owed();
            }
            if (c.balance().signum() > 0) {
                money = money.add(c.balance());
            }
        }
        List<BottleQty> loadQty = bottles.stream().map(b -> new BottleQty(b.code(), b.label(), load.get(b.code()))).toList();
        return new DueList(d, dayName(d), d.equals(today()), new DueCounts(items.size(), listed, done, noBottles, overdue),
                new DueTotals(loadQty, toCollect, owed, Money.of(money)), items);
    }

    /**
     * Customers for {@code d} with their bottles, balance and bills entered on {@code d}, ordered by area then name.
     * {@code all} = every active customer (Delivery Planning "All customers"), otherwise only those due (and, for today,
     * missed). Prices are read only when {@code withPrices} (Enter Bills).
     */
    @Transactional(readOnly = true)
    public List<DueCustomer> customers(LocalDate d, Long areaId, boolean all, boolean withPrices, List<BottleInfo> bottles) {
        return customers(d, areaId, all, withPrices, bottles, null, 0);
    }

    /**
     * My Deliveries (M17): active customers matching {@code q} (code, name, phone or address), with today's bottles, prices
     * and bills – for a stop that is not on today's list. At most {@code limit}.
     */
    @Transactional(readOnly = true)
    public List<DueCustomer> find(String q, int limit) {
        String text = q == null ? "" : q.trim();
        if (text.length() < 2) {
            return List.of();
        }
        return customers(today(), null, true, true, activeBottles(), text, limit);
    }

    private List<DueCustomer> customers(LocalDate d, Long areaId, boolean all, boolean withPrices, List<BottleInfo> bottles, String text,
                                        int limit) {
        boolean isToday = d.equals(today());
        MapSqlParameterSource p = new MapSqlParameterSource("d", d).addValue("dow", d.getDayOfWeek().getValue());
        String sql = "select c.id, c.code, c.name, c.phone, c.address, c.notes, c.area_id, a.name as area_name, c.pay_type, "
                + "c.credit_limit, coalesce(acc.balance, 0) as balance, c.cycle_weeks, c.next_delivery_date, c.registered_on, "
                + "c.delivery_day, c.customer_type_id from customer c join area a on a.id = c.area_id "
                + "left join customer_account acc on acc.customer_id = c.id where c.status = 'ACTIVE'";
        if (!all) {
            sql += " and ((c.delivery_day = :dow and c.registered_on <= :d and mod(:d - c.next_delivery_date, 7 * c.cycle_weeks) = 0)"
                    + (isToday ? " or c.next_delivery_date < :d" : "") + ")";
        }
        if (areaId != null) {
            sql += " and c.area_id = :area";
            p.addValue("area", areaId);
        }
        if (text != null) {
            sql += " and (lower(c.code) like :text or lower(c.name) like :text or lower(c.address) like :text"
                    + " or regexp_replace(c.phone, '[^0-9]', '', 'g') like :digits)";
            String digits = text.replaceAll("[^0-9]", "");
            p.addValue("text", "%" + text.toLowerCase() + "%").addValue("digits", digits.isEmpty() ? "-" : "%" + digits + "%");
        }
        sql += " order by a.name, c.name, c.id";
        if (limit > 0) {
            sql += " limit " + limit;
        }
        List<Row> rows = jdbc.query(sql, p, (rs, i) -> new Row(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("phone"), rs.getString("address"), rs.getString("notes"), rs.getLong("area_id"), rs.getString("area_name"),
                PayType.valueOf(rs.getString("pay_type")), rs.getBigDecimal("credit_limit"), rs.getBigDecimal("balance"),
                rs.getInt("cycle_weeks"), rs.getObject("next_delivery_date", LocalDate.class),
                rs.getObject("registered_on", LocalDate.class), DayOfWeek.of(rs.getInt("delivery_day")), rs.getLong("customer_type_id")));

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
            jdbc.query("select t.id, t.customer_id, t.bill_no, t.paper_bill_no, t.kind, t.entered_on_phone, "
                    + "coalesce(u.full_name, t.created_by) as entered_by from sales_txn t left join app_user u on u.username = t.created_by "
                    + "where t.txn_date = :d and t.customer_id in (:ids) and t.reversed_by_id is null and t.kind in (:kinds) order by t.id",
                    q, rs -> {
                SalesKind kind = SalesKind.valueOf(rs.getString("kind"));
                entered.computeIfAbsent(rs.getLong("customer_id"), k -> new ArrayList<>()).add(new EnteredBill(rs.getLong("id"),
                        rs.getString("bill_no"), rs.getString("paper_bill_no"), kind.name(), kind.label(), rs.getBoolean("entered_on_phone"),
                        rs.getString("entered_by")));
            });
        }
        Map<Long, Map<String, WaterPrice>> prices = Map.of();
        if (withPrices) {
            Map<Long, Long> types = new LinkedHashMap<>();
            rows.forEach(r -> types.put(r.id(), r.customerTypeId()));
            prices = pricing.waterPrices(types, bottles.stream().map(BottleInfo::code).toList(), d);
        }

        List<DueCustomer> items = new ArrayList<>();
        for (Row r : rows) {
            boolean isDue = ScheduleRules.isDue(true, r.registeredOn(), r.day(), r.cycleWeeks(), r.next(), d);
            boolean late = isToday && r.next().isBefore(d);
            List<DueBottle> lines = new ArrayList<>();
            int heldTotal = 0;
            for (BottleInfo b : bottles) {
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
            items.add(new DueCustomer(r.id(), r.code(), r.name(), r.phone(), r.address(), r.notes(), r.areaId(), r.areaName(),
                    r.payType().name(), r.payType().label(), r.creditLimit(), r.balance(), r.day().getValue(), r.cycleWeeks(), r.next(),
                    ScheduleRules.advance(r.next(), r.cycleWeeks(), d), isDue, late,
                    late ? (int) ChronoUnit.DAYS.between(r.next(), d) : 0, isEntered, none, !isEntered && !none, bills, lines));
        }
        return items;
    }

    /**
     * Bottles needed per day from {@code from} for {@code days} days (FR-45, design 5.4): the usual quantities of the
     * customers due and not yet entered on each day (for today also the missed ones). Read with three queries for the
     * whole period, whatever the number of customers.
     */
    @Transactional(readOnly = true)
    public List<DayNeed> needed(LocalDate from, int days, Long areaId) {
        List<DayNeed> out = new ArrayList<>();
        if (days <= 0) {
            return out;
        }
        LocalDate today = today();
        LocalDate to = from.plusDays(days - 1L);
        record Sched(long id, DayOfWeek day, int cycle, LocalDate next, LocalDate registeredOn) {
        }
        MapSqlParameterSource p = new MapSqlParameterSource("from", from).addValue("to", to).addValue("kinds", DELIVERY_KINDS);
        String area = "";
        if (areaId != null) {
            area = " and c.area_id = :area";
            p.addValue("area", areaId);
        }
        List<Sched> custs = jdbc.query("select c.id, c.delivery_day, c.cycle_weeks, c.next_delivery_date, c.registered_on from customer c "
                + "where c.status = 'ACTIVE' and c.registered_on <= :to" + area, p, (rs, i) -> new Sched(rs.getLong(1),
                DayOfWeek.of(rs.getInt(2)), rs.getInt(3), rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)));
        Map<Long, Map<String, Integer>> usual = new HashMap<>();
        jdbc.query("select u.customer_id, u.bottle_type_code, u.qty from customer_usual_qty u join customer c on c.id = u.customer_id "
                + "where c.status = 'ACTIVE' and c.registered_on <= :to and u.qty > 0" + area, p, rs -> {
                    usual.computeIfAbsent(rs.getLong(1), k -> new HashMap<>()).put(rs.getString(2), rs.getInt(3));
                });
        Set<String> entered = new HashSet<>();
        jdbc.query("select distinct t.customer_id, t.txn_date from sales_txn t join customer c on c.id = t.customer_id "
                + "where t.txn_date between :from and :to and t.reversed_by_id is null and t.kind in (:kinds)" + area, p, rs -> {
                    entered.add(rs.getLong(1) + "@" + rs.getObject(2, LocalDate.class));
                });
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            Map<String, Integer> qty = new HashMap<>();
            Map<String, Integer> count = new HashMap<>();
            for (Sched c : custs) {
                boolean due = ScheduleRules.isDue(true, c.registeredOn(), c.day(), c.cycle(), c.next(), d)
                        || (d.equals(today) && c.next().isBefore(d));
                if (!due || entered.contains(c.id() + "@" + d)) {
                    continue;
                }
                usual.getOrDefault(c.id(), Map.of()).forEach((bt, n) -> {
                    qty.merge(bt, n, Integer::sum);
                    count.merge(bt, 1, Integer::sum);
                });
            }
            out.add(new DayNeed(d, qty, count));
        }
        return out;
    }

    /** Store counts of a bottle type: FILLED, FACTORY, EMPTY. */
    @Transactional(readOnly = true)
    public Map<String, Map<String, Integer>> stock() {
        Map<String, Map<String, Integer>> out = new HashMap<>();
        jdbc.query("select bottle_type_code, bucket, qty from stock_balance", rs -> {
            out.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getInt(3));
        });
        return out;
    }

    public static String dayName(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
