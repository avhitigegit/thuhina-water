package lk.thuhina.water.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import lk.thuhina.water.billing.dto.BillingDtos.CustomerPaymentRequest;
import lk.thuhina.water.billing.rules.InvoiceRules;
import lk.thuhina.water.billing.service.InvoiceService;
import lk.thuhina.water.billing.service.PaymentService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.delivery.service.ScheduleService.BottleInfo;
import lk.thuhina.water.demo.dto.DemoDataDtos.DemoDataResult;
import lk.thuhina.water.demo.dto.DemoDataDtos.DemoDataStatus;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchLine;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchRequest;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchView;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnRequest;
import lk.thuhina.water.production.service.ProductionService;
import lk.thuhina.water.sales.dto.SalesDtos.BillLineRequest;
import lk.thuhina.water.sales.dto.SalesDtos.BillRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ExtraOrderRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ProductItemRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ProductSaleRequest;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.service.SalesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Demo-data generator (M12 S4, local / UAT only): a Java port of the prototype's {@code simulate()} – every day of the range
 * the due customers get their delivery bills (some left at the door, some short of empties, an extra bottle now and then),
 * with extra orders, product sales, the weekly factory batch (empties out on Saturday, filled back on Sunday), Credit
 * customers' part payments on the 10th and 25th, Monthly bill customers' payments on the 12th and the monthly invoices on the
 * 1st. Everything goes through the real services, so stock, bottle balances, accounts and the audit log stay consistent.
 * A step that the rules refuse (e.g. not enough filled stock) is skipped and listed. Fixed random seed: the same data each run.
 */
@Service
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataService {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataService.class);
    static final String MSG_RANGE = "The From date must be on or before the To date.";
    static final String MSG_FUTURE = "Demo data is made only for days before today.";
    static final int MAX_DAYS = 120;
    private static final int MAX_PROBLEMS = 20;

    private static final String[] LEFT_NOTES = {"Customer at work – phoned, agreed to leave at the gate",
            "Not home – phoned, left with security guard", "Shop closed – owner agreed by phone, left at side door"};
    private static final String[] SHORT_NOTES = {"One bottle taken to relative's house – will return next trip",
            "Bottle in use upstairs – customer to return next week", "One empty missing – customer checking"};

    private final SalesService sales;
    private final ScheduleService schedule;
    private final ProductionService production;
    private final PaymentService payments;
    private final InvoiceService invoices;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public DemoDataService(SalesService sales, ScheduleService schedule, ProductionService production, PaymentService payments,
                           InvoiceService invoices, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.sales = sales;
        this.schedule = schedule;
        this.production = production;
        this.payments = payments;
        this.invoices = invoices;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public DemoDataStatus status() {
        LocalDate today = BusinessDates.today(clock);
        LocalDate last = jdbc.queryForObject("select max(txn_date) from sales_txn where kind in ('EXCHANGE', 'LEFT_AT_DOOR')", Map.of(),
                LocalDate.class);
        LocalDate from = last != null && last.isAfter(today.minusMonths(2)) ? last.plusDays(1) : today.minusMonths(2);
        return new DemoDataStatus(from, today.minusDays(1), last);
    }

    /** Counts and problems while generating. */
    private static final class Run {
        int bills;
        int left;
        int shortages;
        int extra;
        int products;
        int dispatches;
        int returns;
        int payments;
        int invoices;
        int skipped;
        final List<String> problems = new ArrayList<>();
        int paper;

        void problem(LocalDate d, String what, RuntimeException e) {
            skipped++;
            if (problems.size() < MAX_PROBLEMS) {
                problems.add(BusinessDates.format(d) + " " + what + ": " + e.getMessage());
            }
        }

        String paper() {
            return String.valueOf(++paper);
        }
    }

    public DemoDataResult generate(LocalDate fromDate, LocalDate toDate) {
        LocalDate today = BusinessDates.today(clock);
        LocalDate to = toDate == null ? today.minusDays(1) : toDate;
        LocalDate from = fromDate == null ? today.minusMonths(2) : fromDate;
        if (from.isAfter(to)) {
            throw new ValidationException("from", MSG_RANGE);
        }
        if (!to.isBefore(today)) {
            throw new ValidationException("to", MSG_FUTURE);
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new ValidationException("from", "Choose at most " + MAX_DAYS + " days.");
        }
        Integer existing = jdbc.queryForObject("select count(*) from sales_txn where kind in ('EXCHANGE', 'LEFT_AT_DOOR') "
                + "and txn_date between :f and :t", new MapSqlParameterSource("f", from).addValue("t", to), Integer.class);
        if (existing != null && existing > 0) {
            throw new BusinessException("DEMO_DATA_EXISTS", "Delivery bills already exist between " + BusinessDates.format(from) + " and "
                    + BusinessDates.format(to) + " – choose days without bills.");
        }

        Random rnd = new Random(20261002L);
        Run run = new Run();
        run.paper = nextPaper();
        List<BottleInfo> bottles = schedule.activeBottles();
        String main = bottles.isEmpty() ? null : bottles.getFirst().code();
        List<Long> openBatches = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                receiveBatches(d, openBatches, run);
                continue;
            }
            if (d.getDayOfMonth() == 1) {
                monthlyInvoices(d, today, run);
            }
            deliveries(d, bottles, main, rnd, run);
            extras(d, main, rnd, run);
            payments(d, run);
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY) {
                dispatch(d, openBatches, run);
            }
        }
        LOG.info("Demo data {} – {}: {} bills, {} skipped", from, to, run.bills, run.skipped);
        String message = run.bills + " delivery bills made for " + BusinessDates.format(from) + " – " + BusinessDates.format(to)
                + (run.skipped > 0 ? " (" + run.skipped + " steps skipped)" : "");
        return new DemoDataResult(from, to, run.bills, run.left, run.shortages, run.extra, run.products, run.dispatches, run.returns,
                run.payments, run.invoices, run.skipped, run.problems, message);
    }

    private int nextPaper() {
        Integer max = jdbc.queryForObject("select max(cast(paper_bill_no as integer)) from sales_txn where paper_bill_no ~ '^[0-9]{1,9}$'",
                Map.of(), Integer.class);
        return max == null ? 41000 : max;
    }

    // ------------------------------------------------------------------ deliveries (prototype "Scheduled deliveries")

    private void deliveries(LocalDate d, List<BottleInfo> bottles, String main, Random rnd, Run run) {
        for (DueCustomer c : schedule.customers(d, null, false, false, bottles)) {
            if (!c.due() || c.entered() || c.noBottles() || c.overdue()) {
                continue;
            }
            double r = rnd.nextDouble();
            boolean left = r < 0.06;
            boolean shortOne = !left && r > 0.965;
            List<BillLineRequest> lines = new ArrayList<>();
            for (DueBottle b : c.bottles()) {
                if (b.held() == 0 || b.usual() == 0) {
                    continue;
                }
                // Now and then one bottle more than usual – only when the customer holds enough bottles to return.
                boolean more = b.bottleTypeCode().equals(main) && rnd.nextDouble() < 0.1 && b.held() > b.usual();
                int filled = Math.min(b.usual(), b.held()) + (more ? 1 : 0);
                int empties;
                if (left) {
                    empties = 0;
                } else {
                    empties = filled + b.toCollect() + (b.owed() > 0 && rnd.nextDouble() < 0.6 ? b.owed() : 0);
                    if (shortOne && b.bottleTypeCode().equals(main)) {
                        empties = Math.max(0, empties - 1);
                    }
                }
                lines.add(new BillLineRequest(b.bottleTypeCode(), filled, empties));
            }
            if (lines.isEmpty()) {
                continue;
            }
            String note = left ? LEFT_NOTES[rnd.nextInt(LEFT_NOTES.length)] : shortOne ? SHORT_NOTES[rnd.nextInt(SHORT_NOTES.length)] : null;
            SalesKind kind = left ? SalesKind.LEFT_AT_DOOR : SalesKind.EXCHANGE;
            try {
                BigDecimal paid = BigDecimal.ZERO;
                if ("CASH".equals(c.payType()) && !left) {
                    BigDecimal amount = sales.previewBill(new BillRequest(c.id(), d, null, kind, lines, null, note, true, true)).amount();
                    paid = rnd.nextDouble() < 0.06 ? amount.divide(BigDecimal.valueOf(200), 0, RoundingMode.DOWN).multiply(BigDecimal.valueOf(100))
                            : amount;
                }
                sales.postBill(new BillRequest(c.id(), d, run.paper(), kind, lines, paid, note, true, true));
                run.bills++;
                run.left += left ? 1 : 0;
                run.shortages += shortOne ? 1 : 0;
            } catch (RuntimeException e) {
                run.problem(d, c.code() + " bill", e);
            }
        }
    }

    // ------------------------------------------------------------------ extra orders and product sales

    private void extras(LocalDate d, String main, Random rnd, Run run) {
        if (main == null) {
            return;
        }
        if (d.getDayOfWeek() == DayOfWeek.WEDNESDAY) {
            Long customer = randomCustomer(rnd, "held > 0");
            if (customer != null) {
                try {
                    sales.extraOrder(new ExtraOrderRequest(customer, d, run.paper(), main, 2, 0, 0, null, BigDecimal.ZERO,
                            "Extra for weekend orders", true));
                    run.extra++;
                } catch (RuntimeException e) {
                    run.problem(d, "extra order", e);
                }
            }
        }
        if (d.getDayOfMonth() == 15 || d.getDayOfMonth() == 28) {
            // The cheapest product in stock (pumps, stands), paid in cash.
            Map<String, Object> product = jdbc.query("select code, selling_price from product where active and stock_qty > 0 "
                    + "order by selling_price, code limit 1", Map.of(), rs -> rs.next() ? Map.of("code", rs.getString(1), "price",
                    rs.getBigDecimal(2)) : null);
            Long customer = randomCustomer(rnd, "true");
            if (product != null && customer != null) {
                try {
                    sales.productSale(new ProductSaleRequest(customer, null, d, run.paper(),
                            List.of(new ProductItemRequest((String) product.get("code"), 1, null)), (BigDecimal) product.get("price"),
                            PayMethod.CASH, null, true));
                    run.products++;
                } catch (RuntimeException e) {
                    run.problem(d, "product sale", e);
                }
            }
        }
    }

    /** A random active customer (with a bottle condition on their 20L / main balance). */
    private Long randomCustomer(Random rnd, String condition) {
        List<Long> ids = jdbc.queryForList("select c.id from customer c where c.status = 'ACTIVE' and exists (select 1 from "
                + "customer_bottle_balance bb where bb.customer_id = c.id and " + condition + ") order by c.id", Map.of(), Long.class);
        return ids.isEmpty() ? null : ids.get(rnd.nextInt(ids.size()));
    }

    // ------------------------------------------------------------------ factory (empties out on Saturday, filled back on Sunday)

    private void dispatch(LocalDate d, List<Long> openBatches, Run run) {
        Long factory = jdbc.query("select id from factory where active order by id limit 1", Map.of(), rs -> rs.next() ? rs.getLong(1) : null);
        List<DispatchLine> lines = emptiesFreeFrom(d).entrySet().stream().map(e -> new DispatchLine(e.getKey(), e.getValue())).toList();
        if (factory == null || lines.isEmpty()) {
            return;
        }
        try {
            DispatchView v = production.dispatch(new DispatchRequest(factory, d, "LH-4521", "Weekly batch", lines));
            run.dispatches++;
            v.batches().forEach(b -> openBatches.add(b.id()));
        } catch (RuntimeException e) {
            run.problem(d, "factory dispatch", e);
        }
    }

    /**
     * Empties that can go to the factory on date d, per bottle type (biggest first): the lowest "Empty in store" on any day
     * from d on, the way the Stock report counts it (balance now less the movements after that day). The seed's own
     * factory returns are dated the day after its dispatches, so the balance now can hold empties that are not in the
     * store yet on d (B-10).
     */
    private Map<String, Integer> emptiesFreeFrom(LocalDate d) {
        Map<String, Integer> balance = new LinkedHashMap<>();
        jdbc.query("select s.bottle_type_code, s.qty from stock_balance s join bottle_type b on b.code = s.bottle_type_code "
                + "where b.active and s.bucket = 'EMPTY' and s.qty > 0 order by b.litres desc", Map.of(),
                rs -> { balance.put(rs.getString(1), rs.getInt(2)); });
        Map<String, List<Integer>> later = new HashMap<>();
        jdbc.query("select item_code, sum(qty_delta) from stock_movement where bucket = 'EMPTY' and movement_date > :d "
                + "group by item_code, movement_date order by movement_date desc", Map.of("d", d),
                rs -> { later.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getInt(2)); });
        Map<String, Integer> free = new LinkedHashMap<>();
        balance.forEach((code, qty) -> {
            int q = lowestFrom(qty, later.getOrDefault(code, List.of()));
            if (q > 0) {
                free.put(code, q);
            }
        });
        return free;
    }

    /** The lowest level from a day on: the balance now, stepped back through the later days' changes (newest first). */
    static int lowestFrom(int balanceNow, List<Integer> laterDayChangesNewestFirst) {
        int level = balanceNow;
        int lowest = balanceNow;
        for (int change : laterDayChangesNewestFirst) {
            level -= change;
            lowest = Math.min(lowest, level);
        }
        return lowest;
    }

    private void receiveBatches(LocalDate d, List<Long> openBatches, Run run) {
        List<Long> batches = new ArrayList<>(openBatches);
        openBatches.clear();
        for (Long id : batches) {
            Integer atFactory = jdbc.queryForObject("select b.qty_sent - coalesce(sum(r.filled_qty + r.rejected_qty), 0) from factory_batch b "
                    + "left join factory_return r on r.batch_id = b.id where b.id = :id group by b.qty_sent", Map.of("id", id), Integer.class);
            if (atFactory == null || atFactory <= 0) {
                continue;
            }
            int rejected = atFactory / 75;
            try {
                production.receive(id, new ReturnRequest(d, atFactory - rejected, rejected,
                        rejected > 0 ? "Cracked neck / failed seal test" : null, null));
                run.returns++;
            } catch (RuntimeException e) {
                run.problem(d, "factory return", e);
            }
        }
    }

    // ------------------------------------------------------------------ payments and invoices

    private void payments(LocalDate d, Run run) {
        int day = d.getDayOfMonth();
        if (day == 10 || day == 25) {
            pay(d, "CREDIT", new BigDecimal("0.8"), run);
        }
        if (day == 12) {
            pay(d, "MONTHLY_BILL", BigDecimal.ONE, run);
        }
    }

    private void pay(LocalDate d, String payType, BigDecimal share, Run run) {
        PayMethod[] methods = PayMethod.values();
        List<Map<String, Object>> due = jdbc.queryForList("select c.id, a.balance from customer c join customer_account a on a.customer_id = c.id "
                + "where c.pay_type = :p and c.status = 'ACTIVE' and a.balance >= 100 order by c.id", Map.of("p", payType));
        int i = 0;
        for (Map<String, Object> row : due) {
            BigDecimal amount = ((BigDecimal) row.get("balance")).multiply(share).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                    .multiply(BigDecimal.valueOf(100));
            if (amount.signum() <= 0) {
                continue;
            }
            PayMethod m = methods[i++ % methods.length];
            String ref = m == PayMethod.CASH ? null : m == PayMethod.CHEQUE ? "Cheque " + (440000 + i * 37) : "TRF-" + (650000 + i * 91);
            try {
                payments.receive(new CustomerPaymentRequest(((Number) row.get("id")).longValue(), d, Money.of(amount), m, ref, null, null, null,
                        share.compareTo(BigDecimal.ONE) < 0 ? "Part payment" : "Monthly bill"));
                run.payments++;
            } catch (RuntimeException e) {
                run.problem(d, "payment", e);
            }
        }
    }

    private void monthlyInvoices(LocalDate d, LocalDate today, Run run) {
        String period = YearMonth.from(d).minusMonths(1).toString();
        if (InvoiceRules.periodProblem(period, today) != null) {
            return;
        }
        try {
            run.invoices += invoices.generate(period).created().size();
        } catch (RuntimeException e) {
            run.problem(d, "invoices for " + period, e);
        }
    }
}
