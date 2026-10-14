package lk.thuhina.water.customers.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.customers.dto.CustomerDtos.BottleBalance;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerDetail;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerListResponse;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerListRow;
import lk.thuhina.water.customers.dto.CustomerDtos.HistoryRow;
import lk.thuhina.water.customers.dto.CustomerDtos.LookupRow;
import lk.thuhina.water.customers.dto.CustomerDtos.ScheduledPrice;
import lk.thuhina.water.customers.dto.CustomerDtos.WaterPriceView;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.repository.AreaRepository;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer reads (M05 S5, S6): the list with search, filters and paging (FR-12), the picker lookup, everything for the
 * side panel (FR-11) and the history. The list is one SQL query so it stays fast with many customers.
 */
@Service
public class CustomerQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    /** List filters; every one is optional. {@code status} ACTIVE (default) / INACTIVE / ALL; {@code price} STANDARD / AGREED. */
    public record Filter(String q, Long areaId, Integer day, Long typeId, PayType payType, String status, String price) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final CustomerRepository customers;
    private final AreaRepository areas;
    private final CustomerTypeRepository types;
    private final BottleTypeRepository bottleTypes;
    private final PricingService pricing;
    private final AppUserRepository users;
    private final Clock clock;

    public CustomerQueryService(NamedParameterJdbcTemplate jdbc, CustomerRepository customers, AreaRepository areas,
                                CustomerTypeRepository types, BottleTypeRepository bottleTypes, PricingService pricing,
                                AppUserRepository users, Clock clock) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.areas = areas;
        this.types = types;
        this.bottleTypes = bottleTypes;
        this.pricing = pricing;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public CustomerListResponse list(Filter f, int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        LocalDate today = today();
        MapSqlParameterSource p = new MapSqlParameterSource("today", today);
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().trim().toLowerCase(Locale.ROOT);
            String digits = q.replaceAll("\\D", "");
            where.append(" and (lower(c.name || ' ' || c.code || ' ' || c.address) like :q escape '\\'");
            p.addValue("q", "%" + likeEscape(q) + "%");
            if (digits.length() >= 3) {
                where.append(" or regexp_replace(c.phone, '[^0-9]', '', 'g') like :digits");
                p.addValue("digits", "%" + digits + "%");
            }
            where.append(")");
        }
        if (f.areaId() != null) {
            where.append(" and c.area_id = :area");
            p.addValue("area", f.areaId());
        }
        if (f.day() != null) {
            where.append(" and c.delivery_day = :day");
            p.addValue("day", f.day());
        }
        if (f.typeId() != null) {
            where.append(" and c.customer_type_id = :type");
            p.addValue("type", f.typeId());
        }
        if (f.payType() != null) {
            where.append(" and c.pay_type = :pay");
            p.addValue("pay", f.payType().name());
        }
        String status = f.status() == null || f.status().isBlank() ? "ACTIVE" : f.status().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(status)) {
            where.append(" and c.status = :status");
            p.addValue("status", status);
        }
        String agreedNow = "exists (select 1 from customer_price cp where cp.customer_id = c.id and not cp.cancelled "
                + "and cp.valid_from <= :today and (cp.valid_until is null or cp.valid_until >= :today))";
        if ("AGREED".equalsIgnoreCase(f.price())) {
            where.append(" and ").append(agreedNow);
        } else if ("STANDARD".equalsIgnoreCase(f.price())) {
            where.append(" and not ").append(agreedNow);
        }

        String from = " from customer c join area a on a.id = c.area_id join customer_type t on t.id = c.customer_type_id "
                + "left join customer_account acc on acc.customer_id = c.id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, p, Long.class);
        String order = switch (sort == null ? "" : sort) {
            case "code" -> "c.code";
            case "area" -> "lower(a.name), lower(c.name)";
            case "next" -> "c.next_delivery_date, lower(c.name)";
            default -> "lower(c.name), c.code";
        };
        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        List<Object[]> base = jdbc.query("select c.id, c.code, c.name, c.address, a.name as area_name, c.phone, t.name as type_name, "
                        + "c.delivery_day, c.cycle_weeks, c.next_delivery_date, c.pay_type, c.credit_limit, c.status, c.status_date, "
                        + "c.status_reason, coalesce(acc.balance, 0) as balance" + from + where + " order by " + order
                        + " limit :limit offset :offset",
                p, (rs, i) -> new Object[] {rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("address"),
                        rs.getString("area_name"), rs.getString("phone"), rs.getString("type_name"), rs.getInt("delivery_day"),
                        rs.getInt("cycle_weeks"), rs.getObject("next_delivery_date", LocalDate.class), rs.getString("pay_type"),
                        rs.getBigDecimal("credit_limit"), rs.getString("status"), rs.getObject("status_date", LocalDate.class),
                        rs.getString("status_reason"), rs.getBigDecimal("balance")});
        List<Long> ids = base.stream().map(r -> (Long) r[0]).toList();
        Map<Long, List<BottleBalance>> balances = bottleBalances(ids);
        Map<Long, String> agreed = agreedText(ids, today);

        List<CustomerListRow> items = new ArrayList<>();
        for (Object[] r : base) {
            long id = (Long) r[0];
            List<BottleBalance> b = balances.getOrDefault(id, List.of());
            BigDecimal balance = Money.of((BigDecimal) r[15]);
            BigDecimal limit = (BigDecimal) r[11];
            items.add(new CustomerListRow(id, (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5], (String) r[6],
                    (Integer) r[7], (Integer) r[8], (LocalDate) r[9], (String) r[10], limit, (String) r[12], (LocalDate) r[13],
                    (String) r[14], b, b.stream().mapToInt(BottleBalance::toCollect).sum(), b.stream().mapToInt(BottleBalance::owed).sum(),
                    balance, !PayType.CASH.name().equals(r[10]) && balance.compareTo(limit) > 0, agreed.get(id)));
        }
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("select status, count(*) from customer group by status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        long active = counts.getOrDefault("ACTIVE", 0L);
        long inactive = counts.getOrDefault("INACTIVE", 0L);
        return new CustomerListResponse(items, safePage, safeSize, total == null ? 0 : total, active + inactive, active, inactive);
    }

    /**
     * Picker lookup: an exact code ({@code C0017}), a number ({@code 17} → C0017), phone digits (9 or more), or a name
     * containing the text. At most 10, active customers first.
     */
    @Transactional(readOnly = true)
    public List<LookupRow> lookup(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = text.trim();
        String sql = "select c.id, c.code, c.name, c.phone, a.name as area_name, c.status from customer c "
                + "join area a on a.id = c.area_id where ";
        MapSqlParameterSource p = new MapSqlParameterSource();
        String digits = t.replaceAll("\\D", "");
        if (t.matches("(?i)C\\d{1,6}")) {
            sql += "upper(c.code) = :code";
            p.addValue("code", t.toUpperCase(Locale.ROOT));
        } else if (t.matches("\\d{1,6}")) {
            sql += "c.code = :code";
            p.addValue("code", String.format("C%04d", Integer.parseInt(t)));
        } else if (digits.length() >= 9 && t.replaceAll("[\\d\\s]", "").isEmpty()) {
            sql += "regexp_replace(c.phone, '[^0-9]', '', 'g') = :digits";
            p.addValue("digits", digits);
        } else {
            sql += "lower(c.name) like :q escape '\\'";
            p.addValue("q", "%" + likeEscape(t.toLowerCase(Locale.ROOT)) + "%");
        }
        return jdbc.query(sql + " order by case when c.status = 'ACTIVE' then 0 else 1 end, lower(c.name) limit 10", p,
                (rs, i) -> new LookupRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("phone"),
                        rs.getString("area_name"), rs.getString("status")));
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public CustomerDetail detail(long id) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
        LocalDate today = today();
        BigDecimal balance = jdbc.query("select balance from customer_account where customer_id = :c",
                new MapSqlParameterSource("c", id), rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO);
        BigDecimal outstanding = Money.of(balance);
        List<BottleType> active = bottleTypes.findByActiveTrueOrderByLitresDesc();
        List<WaterPriceView> prices = new ArrayList<>();
        for (BottleType b : active) {
            Optional<PricingService.WaterPrice> wp = pricing.waterPrice(id, b.getCode(), today);
            prices.add(new WaterPriceView(b.getCode(), LedgerRules.bottleLabel(b.getLitres()), wp.map(PricingService.WaterPrice::price).orElse(null),
                    wp.map(PricingService.WaterPrice::agreed).orElse(false), wp.map(PricingService.WaterPrice::standard).orElse(null),
                    nextAgreed(id, b.getCode(), today),
                    ScheduleRules.bottlesPerMonth(c.getUsualQty().getOrDefault(b.getCode(), 0), c.getCycleWeeks()),
                    fillingCharge(b.getCode(), today)));
        }
        boolean overLimit = c.getPayType() != PayType.CASH && outstanding.compareTo(c.getCreditLimit()) > 0;
        boolean overdue = c.isActive() && c.getNextDeliveryDate().isBefore(today);
        return new CustomerDetail(c.getId(), c.getCode(), c.getName(), c.getAddress(), c.getAreaId(),
                areas.findById(c.getAreaId()).map(a -> a.getName()).orElse(""), c.getPhone(), c.getCustomerTypeId(),
                types.findById(c.getCustomerTypeId()).map(t -> t.getName()).orElse(""), c.getDeliveryDay(), c.getCycleWeeks(),
                c.getNextDeliveryDate(), new LinkedHashMap<>(c.getUsualQty()), c.getPayType().name(), c.getCreditLimit(),
                c.getTermsDays(), c.getRegisteredOn(), c.getStatus().name(), c.getStatusDate(), c.getStatusReason(), c.getNotes(),
                c.isWhatsappOptIn(), c.getWhatsappNumber(), bottleBalances(List.of(id)).getOrDefault(id, List.of()),
                outstanding, overLimit, overdue, prices, c.getVersion());
    }

    /** Customer events from the audit log (registration, changes, status, prices), bills and payments – newest first. */
    @Transactional(readOnly = true)
    public List<HistoryRow> history(long id) {
        Customer c = customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
        List<HistoryRow> raw = jdbc.query("select ts, action, entity, details, username from audit_log "
                        + "where (entity = 'Customer' and ref = :code) or (entity = 'Customer price' and ref like :prefix) "
                        + "order by ts desc, id desc",
                new MapSqlParameterSource("code", c.getCode()).addValue("prefix", c.getCode() + " %"),
                (rs, i) -> new HistoryRow(rs.getTimestamp("ts").toInstant(),
                        rs.getTimestamp("ts").toInstant().atZone(BusinessDates.ZONE).toLocalDate(), kind(rs),
                        rs.getString("details"), rs.getString("username"), null, null, null, null, null, null));
        List<HistoryRow> all = new ArrayList<>(raw);
        all.addAll(bills(id));
        all.sort(Comparator.comparing(HistoryRow::date).thenComparing(HistoryRow::at).reversed());
        Set<String> who = all.stream().map(HistoryRow::by).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> names = users.findByUsernameIn(who).stream().collect(Collectors.toMap(AppUser::getUsername, AppUser::getFullName));
        return all.stream().map(h -> new HistoryRow(h.at(), h.date(), h.kind(), h.description(), h.by(), names.get(h.by()), h.txnId(),
                h.billNo(), h.amount(), h.paid(), h.balance())).toList();
    }

    /**
     * The customer's bills (kind SALE) and payments (kind PAYMENT), oldest first, each with the account balance after it
     * (running Σ amount − paid on bills − payments).
     */
    private List<HistoryRow> bills(long customerId) {
        record Entry(HistoryRow row, BigDecimal change) {
        }
        List<Entry> raw = new ArrayList<>();
        MapSqlParameterSource p = new MapSqlParameterSource("c", customerId);
        jdbc.query("select id, bill_no, paper_bill_no, kind, txn_date, amount, paid_on_bill, note, reversed_by_id, created_at, created_by "
                        + "from sales_txn where customer_id = :c order by txn_date, id", p, rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    BigDecimal paid = rs.getBigDecimal("paid_on_bill");
                    String paper = rs.getString("paper_bill_no");
                    String note = rs.getString("note");
                    String text = SalesKind.valueOf(rs.getString("kind")).label() + " – bill " + rs.getString("bill_no")
                            + (paper == null ? "" : " (paper " + paper + ")") + (rs.getObject("reversed_by_id") == null ? "" : " – reversed")
                            + (note == null ? "" : ": " + note);
                    raw.add(new Entry(new HistoryRow(rs.getTimestamp("created_at").toInstant(), rs.getObject("txn_date", LocalDate.class),
                            "SALE", text, rs.getString("created_by"), null, rs.getLong("id"), rs.getString("bill_no"), amount, paid, null),
                            amount.subtract(paid)));
                });
        jdbc.query("select receipt_no, pay_date, amount, method, reference, note, created_at, created_by from customer_payment "
                        + "where customer_id = :c order by pay_date, id", p, rs -> {
                    BigDecimal amount = rs.getBigDecimal("amount");
                    String ref = rs.getString("reference");
                    String note = rs.getString("note");
                    String text = "Payment " + rs.getString("receipt_no") + " – " + PayMethod.valueOf(rs.getString("method")).label()
                            + (ref == null ? "" : " (" + ref + ")") + (note == null ? "" : ": " + note);
                    raw.add(new Entry(new HistoryRow(rs.getTimestamp("created_at").toInstant(), rs.getObject("pay_date", LocalDate.class),
                            "PAYMENT", text, rs.getString("created_by"), null, null, rs.getString("receipt_no"), null, amount, null),
                            amount.negate()));
                });
        raw.sort(Comparator.comparing((Entry m) -> m.row().date()).thenComparing(m -> m.row().at()));
        List<HistoryRow> out = new ArrayList<>();
        BigDecimal running = Money.ZERO;
        for (Entry m : raw) {
            running = running.add(m.change());
            HistoryRow h = m.row();
            out.add(new HistoryRow(h.at(), h.date(), h.kind(), h.description(), h.by(), null, h.txnId(), h.billNo(), h.amount(), h.paid(),
                    Money.of(running)));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static String kind(ResultSet rs) throws SQLException {
        if ("Customer price".equals(rs.getString("entity"))) {
            return "PRICE";
        }
        return "CREATE".equals(rs.getString("action")) ? "REGISTERED" : "CHANGED";
    }

    /** Held / to collect / owed per bottle type for these customers: active bottle types, plus any with bottles. */
    private Map<Long, List<BottleBalance>> bottleBalances(List<Long> ids) {
        Map<Long, List<BottleBalance>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbc.query("select b.customer_id, b.bottle_type_code, bt.litres, bt.active, b.held, b.to_collect, b.owed "
                        + "from customer_bottle_balance b join bottle_type bt on bt.code = b.bottle_type_code "
                        + "where b.customer_id in (:ids) order by bt.litres desc",
                new MapSqlParameterSource("ids", ids),
                rs -> {
                    int held = rs.getInt("held");
                    int toCollect = rs.getInt("to_collect");
                    int owed = rs.getInt("owed");
                    if (rs.getBoolean("active") || held + toCollect + owed > 0) {
                        out.computeIfAbsent(rs.getLong("customer_id"), k -> new ArrayList<>()).add(new BottleBalance(
                                rs.getString("bottle_type_code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres")), held, toCollect, owed));
                    }
                });
        return out;
    }

    /** "20L Rs. 270.00" for customers with an agreed price today. */
    private Map<Long, String> agreedText(List<Long> ids, LocalDate today) {
        Map<Long, List<String>> parts = new HashMap<>();
        if (ids.isEmpty()) {
            return Map.of();
        }
        jdbc.query("select cp.customer_id, bt.litres, cp.price from customer_price cp join bottle_type bt on bt.code = cp.bottle_type_code "
                        + "where cp.customer_id in (:ids) and not cp.cancelled and cp.valid_from <= :today "
                        + "and (cp.valid_until is null or cp.valid_until >= :today) order by bt.litres desc",
                new MapSqlParameterSource("ids", ids).addValue("today", today),
                rs -> {
                    parts.computeIfAbsent(rs.getLong("customer_id"), k -> new ArrayList<>())
                            .add(LedgerRules.bottleLabel(rs.getBigDecimal("litres")) + " " + Money.format(rs.getBigDecimal("price")));
                });
        Map<Long, String> out = new HashMap<>();
        parts.forEach((k, v) -> out.put(k, String.join(", ", v)));
        return out;
    }

    private ScheduledPrice nextAgreed(long customerId, String bottle, LocalDate today) {
        return jdbc.query("select price, valid_from from customer_price where customer_id = :c and bottle_type_code = :bt "
                        + "and not cancelled and valid_from > :today order by valid_from, id limit 1",
                new MapSqlParameterSource("c", customerId).addValue("bt", bottle).addValue("today", today),
                rs -> rs.next() ? new ScheduledPrice(rs.getBigDecimal(1), rs.getObject(2, LocalDate.class)) : null);
    }

    /** The filling charge of the first active factory today – a guide for the lowest sensible agreed price. */
    private BigDecimal fillingCharge(String bottle, LocalDate today) {
        return jdbc.query("select fc.charge from factory_charge fc join factory f on f.id = fc.factory_id "
                        + "where f.active and fc.bottle_type_code = :bt and fc.effective_from <= :today "
                        + "order by f.code, fc.effective_from desc, fc.id desc limit 1",
                new MapSqlParameterSource("bt", bottle).addValue("today", today),
                rs -> rs.next() ? rs.getBigDecimal(1) : null);
    }

    private static String likeEscape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private LocalDate today() {
        return BusinessDates.today(clock);
    }
}
