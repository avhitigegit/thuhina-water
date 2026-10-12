package lk.thuhina.water.delivery.service;

import java.math.BigDecimal;
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
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.delivery.dto.DeliveryDtos.BottleQty;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.DeliveryDtos.LastDelivery;
import lk.thuhina.water.delivery.dto.DeliveryDtos.PlanKpis;
import lk.thuhina.water.delivery.dto.DeliveryDtos.PlanRoute;
import lk.thuhina.water.delivery.dto.DeliveryDtos.PlanRow;
import lk.thuhina.water.delivery.dto.DeliveryDtos.Planning;
import lk.thuhina.water.delivery.dto.DeliveryDtos.ReadyCheck;
import lk.thuhina.water.delivery.dto.DeliveryDtos.Requirement;
import lk.thuhina.water.delivery.dto.DeliveryDtos.RequirementDay;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.delivery.service.ScheduleService.BottleInfo;
import lk.thuhina.water.delivery.service.ScheduleService.DayNeed;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.sales.model.SalesKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery Planning (FR-58) and the upcoming bottle requirement (FR-45), design 5.4: who is due on a date grouped by route,
 * with last / next / week-after deliveries, KPIs and a ready check per bottle type; and the filled bottles needed day by
 * day against the stock in store and at the factory.
 */
@Service
public class PlanningService {

    public static final String SHOW_DUE = "due";
    public static final String SHOW_ALL = "all";
    public static final int MAX_DAYS = 60;

    private final ScheduleService schedule;
    private final NamedParameterJdbcTemplate jdbc;

    public PlanningService(ScheduleService schedule, NamedParameterJdbcTemplate jdbc) {
        this.schedule = schedule;
        this.jdbc = jdbc;
    }

    /** Planning for {@code date} (default tomorrow), one route or all, customers due or all active customers. */
    @Transactional(readOnly = true)
    public Planning planning(LocalDate date, Long areaId, String show) {
        LocalDate today = schedule.today();
        LocalDate d = date == null ? today.plusDays(1) : date;
        String mode = show == null || show.isBlank() ? SHOW_DUE : show.trim().toLowerCase();
        if (!mode.equals(SHOW_DUE) && !mode.equals(SHOW_ALL)) {
            throw new ValidationException("show", "Show must be 'due' or 'all'.");
        }
        List<BottleInfo> bottles = schedule.activeBottles();
        List<DueCustomer> customers = schedule.customers(d, areaId, mode.equals(SHOW_ALL), false, bottles);
        // In "all" mode a customer is on the due list when due on the date or (today) missed.
        List<DueCustomer> due = customers.stream().filter(c -> c.due() || c.overdue()).toList();
        Map<Long, LastDelivery> last = lastDeliveries(customers.stream().map(DueCustomer::id).toList(), d);

        // KPIs (customers due) and what is still to load (due, not entered).
        Map<String, Integer> needed = new HashMap<>();
        int entered = 0;
        int missed = 0;
        int toCollect = 0;
        int owed = 0;
        BigDecimal money = Money.ZERO;
        for (DueCustomer c : due) {
            entered += c.entered() ? 1 : 0;
            missed += c.overdue() ? 1 : 0;
            for (DueBottle b : c.bottles()) {
                if (!c.entered()) {
                    needed.merge(b.bottleTypeCode(), b.usual(), Integer::sum);
                }
                toCollect += b.toCollect();
                owed += b.owed();
            }
            if (c.balance().signum() > 0) {
                money = money.add(c.balance());
            }
        }
        PlanKpis kpis = new PlanKpis(due.size(), entered, missed, qty(bottles, needed), toCollect, owed, Money.of(money));

        // Ready check: bottles still needed from today up to the day before the date.
        Map<String, Integer> before = new HashMap<>();
        if (d.isAfter(today)) {
            for (DayNeed n : schedule.needed(today, (int) ChronoUnit.DAYS.between(today, d), null)) {
                n.qty().forEach((bt, q) -> before.merge(bt, q, Integer::sum));
            }
        }
        Map<String, Map<String, Integer>> stock = schedule.stock();
        List<ReadyCheck> ready = new ArrayList<>();
        for (BottleInfo b : bottles) {
            int need = needed.getOrDefault(b.code(), 0);
            int earlier = before.getOrDefault(b.code(), 0);
            if (need == 0 && earlier == 0) {
                continue;
            }
            Map<String, Integer> s = stock.getOrDefault(b.code(), Map.of());
            int filled = s.getOrDefault("FILLED", 0);
            int factory = s.getOrDefault("FACTORY", 0);
            int shortBy = Math.max(0, need - (filled - earlier));
            ready.add(new ReadyCheck(b.code(), b.label(), b.name(), need, earlier, filled, factory, shortBy, shortBy == 0,
                    shortBy > 0 && factory >= shortBy));
        }

        // Rows grouped by route (area), in the order of the list (area, then name).
        Map<Long, List<PlanRow>> byArea = new LinkedHashMap<>();
        Map<Long, String> areaNames = new HashMap<>();
        for (DueCustomer c : customers) {
            areaNames.put(c.areaId(), c.areaName());
            byArea.computeIfAbsent(c.areaId(), k -> new ArrayList<>()).add(row(c, d, last.get(c.id())));
        }
        List<PlanRoute> routes = new ArrayList<>();
        Map<Long, DueCustomer> byId = new HashMap<>();
        customers.forEach(c -> byId.put(c.id(), c));
        for (Map.Entry<Long, List<PlanRow>> e : byArea.entrySet()) {
            Map<String, Integer> load = new HashMap<>();
            int dueCount = 0;
            int toLoad = 0;
            int collect = 0;
            for (PlanRow r : e.getValue()) {
                if (!r.due()) {
                    continue;
                }
                dueCount++;
                if (!r.entered()) {
                    toLoad++;
                    collect += r.toCollect();
                    for (DueBottle b : byId.get(r.id()).bottles()) {
                        load.merge(b.bottleTypeCode(), b.usual(), Integer::sum);
                    }
                }
            }
            routes.add(new PlanRoute(e.getKey(), areaNames.get(e.getKey()), dueCount, toLoad, nonZero(bottles, load), collect, e.getValue()));
        }
        return new Planning(d, ScheduleService.dayName(d), d.equals(today), mode, customers.size(), kpis, ready, routes);
    }

    private PlanRow row(DueCustomer c, LocalDate d, LastDelivery last) {
        boolean due = c.due() || c.overdue();
        List<BottleQty> usual = new ArrayList<>();
        List<BottleQty> held = new ArrayList<>();
        int collect = 0;
        int owed = 0;
        for (DueBottle b : c.bottles()) {
            if (due && b.usual() > 0) {
                usual.add(new BottleQty(b.bottleTypeCode(), b.label(), b.usual()));
            }
            if (b.held() > 0) {
                held.add(new BottleQty(b.bottleTypeCode(), b.label(), b.held()));
            }
            collect += b.toCollect();
            owed += b.owed();
        }
        String status;
        String label;
        if (c.entered()) {
            status = "ENTERED";
            label = "Entered";
        } else if (c.overdue()) {
            status = "MISSED";
            label = "Missed " + BusinessDates.format(c.nextDeliveryDate());
        } else if (c.due()) {
            status = c.noBottles() ? "FIRST_DELIVERY" : "DUE";
            label = c.noBottles() ? "First delivery" : "Due";
        } else {
            status = "NOT_DUE";
            label = "Not due";
        }
        PayType pay = PayType.valueOf(c.payType());
        boolean overLimit = pay != PayType.CASH && Money.isPositive(c.creditLimit()) && c.balance().compareTo(c.creditLimit()) > 0;
        LocalDate next = ScheduleRules.advance(c.nextDeliveryDate(), c.cycleWeeks(), d);
        return new PlanRow(c.id(), c.code(), c.name(), c.address(), c.phone(), c.payType(), c.payTypeLabel(), c.deliveryDay(),
                DayOfWeek.of(c.deliveryDay()).getDisplayName(TextStyle.FULL, Locale.ENGLISH), c.cycleWeeks(), c.nextDeliveryDate(), last, due, c.overdue(),
                c.entered(), c.enteredBills(), usual, held, next, next.plusWeeks(c.cycleWeeks()), collect, owed, c.balance(), status,
                label, overLimit);
    }

    /** The latest non-reversed delivery bill of each customer dated before {@code d}, with the filled bottles given. */
    private Map<Long, LastDelivery> lastDeliveries(List<Long> ids, LocalDate d) {
        Map<Long, LastDelivery> out = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        jdbc.query("select code, litres from bottle_type", rs -> {
            labels.put(rs.getString(1), LedgerRules.bottleLabel(rs.getBigDecimal(2)));
        });
        for (int i = 0; i < ids.size(); i += ScheduleService.CHUNK) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + ScheduleService.CHUNK));
            record Txn(long id, long customerId, LocalDate date, boolean leftAtDoor) {
            }
            List<Txn> txns = jdbc.query("select distinct on (customer_id) id, customer_id, txn_date, kind from sales_txn "
                            + "where customer_id in (:ids) and txn_date < :d and reversed_by_id is null and kind in (:kinds) "
                            + "order by customer_id, txn_date desc, id desc",
                    new MapSqlParameterSource("ids", chunk).addValue("d", d).addValue("kinds", ScheduleService.DELIVERY_KINDS),
                    (rs, n) -> new Txn(rs.getLong("id"), rs.getLong("customer_id"), rs.getObject("txn_date", LocalDate.class),
                            SalesKind.LEFT_AT_DOOR.name().equals(rs.getString("kind"))));
            if (txns.isEmpty()) {
                continue;
            }
            Map<Long, List<BottleQty>> filled = new HashMap<>();
            jdbc.query("select l.txn_id, l.bottle_type_code, sum(l.filled) from sales_txn_line l join bottle_type b on b.code = l.bottle_type_code "
                            + "where l.txn_id in (:t) group by l.txn_id, l.bottle_type_code, b.litres having sum(l.filled) > 0 "
                            + "order by l.txn_id, b.litres desc",
                    new MapSqlParameterSource("t", txns.stream().map(Txn::id).toList()), rs -> {
                        filled.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                                .add(new BottleQty(rs.getString(2), labels.get(rs.getString(2)), rs.getInt(3)));
                    });
            for (Txn t : txns) {
                out.put(t.customerId(), new LastDelivery(t.date(), filled.getOrDefault(t.id(), List.of()), t.leftAtDoor()));
            }
        }
        return out;
    }

    /**
     * Filled bottles of one type (default the largest active one) needed for the next {@code days} days from today
     * (FR-45): customers due and not entered, bottles needed, running total, filled left in store and with the factory.
     */
    @Transactional(readOnly = true)
    public Requirement requirement(Integer days, String bottle) {
        int n = days == null ? 14 : days;
        if (n < 1 || n > MAX_DAYS) {
            throw new ValidationException("days", "Days must be between 1 and " + MAX_DAYS + ".");
        }
        List<BottleInfo> bottles = schedule.activeBottles();
        BottleInfo b = bottle == null || bottle.isBlank() ? (bottles.isEmpty() ? null : bottles.getFirst())
                : bottles.stream().filter(x -> x.code().equalsIgnoreCase(bottle.trim())).findFirst().orElse(null);
        if (b == null) {
            throw new ValidationException("bottle", "Choose an active bottle type.");
        }
        Map<String, Integer> s = schedule.stock().getOrDefault(b.code(), Map.of());
        int filled = s.getOrDefault("FILLED", 0);
        int factory = s.getOrDefault("FACTORY", 0);
        int running = 0;
        LocalDate runsOut = null;
        List<RequirementDay> rows = new ArrayList<>();
        for (DayNeed day : schedule.needed(schedule.today(), n, null)) {
            int need = day.qty().getOrDefault(b.code(), 0);
            running += need;
            int left = filled - running;
            int all = filled + factory - running;
            if (all < 0 && runsOut == null) {
                runsOut = day.date();
            }
            rows.add(new RequirementDay(day.date(), ScheduleService.dayName(day.date()), day.customers().getOrDefault(b.code(), 0), need,
                    running, left, all));
        }
        return new Requirement(b.code(), b.label(), b.name(), filled, factory, s.getOrDefault("EMPTY", 0), n, rows, runsOut);
    }

    private static List<BottleQty> qty(List<BottleInfo> bottles, Map<String, Integer> q) {
        return bottles.stream().map(b -> new BottleQty(b.code(), b.label(), q.getOrDefault(b.code(), 0))).toList();
    }

    private static List<BottleQty> nonZero(List<BottleInfo> bottles, Map<String, Integer> q) {
        return qty(bottles, q).stream().filter(x -> x.qty() > 0).toList();
    }

    /** "10 × 20L, 2 × 10L"; empty text for none. */
    public static String text(List<BottleQty> q) {
        return String.join(", ", q.stream().filter(x -> x.qty() > 0).map(x -> x.qty() + " × " + x.label()).toList());
    }
}
