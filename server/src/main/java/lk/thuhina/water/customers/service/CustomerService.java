package lk.thuhina.water.customers.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerSaved;
import lk.thuhina.water.customers.dto.CustomerDtos.DeactivateRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.SetCustomerPriceRequest;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.masterdata.model.Area;
import lk.thuhina.water.masterdata.model.CustomerType;
import lk.thuhina.water.masterdata.repository.AreaRepository;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Permissions;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customers (FR-08 – FR-13): register and edit with every check of the prototype, duplicate-phone warning, code
 * {@code C0001…}, bottle balance and account rows, deactivate / reactivate. Every change is audited with the fields
 * that changed.
 */
@Service
public class CustomerService {

    public static final String DUP_PHONE = "DUP_PHONE";
    static final String ENTITY = "Customer";
    public static final String MSG_PHONE = "Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471.";
    private static final Pattern PHONE = Pattern.compile("^0\\d{2} ?\\d{3} ?\\d{4}$");
    private static final List<Integer> CREDIT_TERMS = List.of(7, 14, 30, 45, 60);

    private final CustomerRepository repository;
    private final AreaRepository areas;
    private final CustomerTypeRepository types;
    private final BottleTypeRepository bottleTypes;
    private final CustomerPriceService prices;
    private final CustomerQueryService queries;
    private final PricingService pricing;
    private final NumberingService numbering;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CustomerService(CustomerRepository repository, AreaRepository areas, CustomerTypeRepository types,
                           BottleTypeRepository bottleTypes, CustomerPriceService prices, CustomerQueryService queries,
                           PricingService pricing, NumberingService numbering, AuditService audit,
                           NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.areas = areas;
        this.types = types;
        this.bottleTypes = bottleTypes;
        this.prices = prices;
        this.queries = queries;
        this.pricing = pricing;
        this.numbering = numbering;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Registers a customer. Agreed prices given here apply from the registration date (before any first-purchase bill)
     * when they differ from the standard price; they need the right to set agreed prices.
     */
    @Transactional
    public CustomerSaved create(CustomerRequest r) {
        LocalDate today = today();
        LocalDate registeredOn = r.registeredOn() == null ? today : r.registeredOn();
        if (registeredOn.isAfter(today)) {
            throw new ValidationException("registeredOn", "Registration date cannot be in the future.");
        }
        Checked v = check(r, null);
        Map<String, BigDecimal> agreed = agreedPrices(r, v.type(), registeredOn);

        Customer c = new Customer(numbering.next(SequenceNames.CUSTOMER), registeredOn);
        apply(c, r, v);
        c = repository.saveAndFlush(c);
        MapSqlParameterSource p = new MapSqlParameterSource("c", c.getId());
        jdbc.update("insert into customer_bottle_balance (customer_id, bottle_type_code) select :c, code from bottle_type "
                + "on conflict do nothing", p);
        jdbc.update("insert into customer_account (customer_id, balance) values (:c, 0)", p);
        audit.log(AuditAction.CREATE, ENTITY, c.getCode(), c.getName() + " – " + v.type().getName() + ", " + v.area().getName()
                + ", " + c.getPayType().label());

        String reason = Texts.blankToNull(r.agreedReason());
        for (Map.Entry<String, BigDecimal> e : agreed.entrySet()) {
            prices.set(c.getId(), new SetCustomerPriceRequest(e.getKey(), e.getValue(), registeredOn, null, reason, null));
        }
        return new CustomerSaved(queries.detail(c.getId()), agreed.size());
    }

    /** Edit details, delivery and payment. Changed fields are listed in the audit log. */
    @Transactional
    public CustomerSaved update(long id, CustomerRequest r) {
        Customer c = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(r.version(), c.getVersion(), ENTITY + " " + c.getCode());
        Checked v = check(r, c);
        List<String> changes = changes(c, r, v);
        if (changes.isEmpty()) {
            return new CustomerSaved(queries.detail(id), 0);
        }
        apply(c, r, v);
        repository.saveAndFlush(c);
        audit.log(AuditAction.UPDATE, ENTITY, c.getCode(), String.join("; ", changes));
        return new CustomerSaved(queries.detail(id), 0);
    }

    /** FR-13: inactive from a date (not in the future) with a reason; history is kept. */
    @Transactional
    public void deactivate(long id, DeactivateRequest r) {
        Customer c = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        if (r.date().isAfter(today())) {
            throw new ValidationException("date", "The status date cannot be in the future.");
        }
        String reason = r.reason().trim();
        c.deactivate(r.date(), reason);
        repository.saveAndFlush(c);
        audit.log(AuditAction.UPDATE, ENTITY, c.getCode(), "Deactivated from " + BusinessDates.format(r.date()) + " – " + reason
                + ". History kept.");
    }

    /** Reactivate; a past next delivery date moves to the next delivery day from today. */
    @Transactional
    public void reactivate(long id) {
        Customer c = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        if (c.isActive()) {
            throw new BusinessException("ALREADY_ACTIVE", c.getName() + " is already active.");
        }
        LocalDate today = today();
        LocalDate next = c.getNextDeliveryDate().isBefore(today)
                ? ScheduleRules.nextAligned(c.getNextDeliveryDate(), c.getCycleWeeks(), today.minusDays(1))
                : c.getNextDeliveryDate();
        c.reactivate(today, next);
        repository.saveAndFlush(c);
        audit.log(AuditAction.UPDATE, ENTITY, c.getCode(), "Reactivated from " + BusinessDates.format(today)
                + "; next delivery " + BusinessDates.format(next) + ". History kept.");
    }

    // ------------------------------------------------------------------ checks

    private record Checked(Area area, CustomerType type, String phone, BigDecimal creditLimit, int termsDays,
                           Map<String, Integer> usual, String whatsappNumber) {
    }

    /** All FR-08 – FR-10 checks; {@code existing} = null for a new customer. */
    private Checked check(CustomerRequest r, Customer existing) {
        String phone = r.phone().trim();
        if (!PHONE.matcher(phone).matches()) {
            throw new ValidationException("phone", MSG_PHONE);
        }
        Area area = areas.findById(r.areaId())
                .filter(a -> a.isActive() || (existing != null && a.getId().equals(existing.getAreaId())))
                .orElseThrow(() -> new ValidationException("areaId", "Choose an active area."));
        CustomerType type = types.findById(r.customerTypeId())
                .filter(t -> t.isActive() || (existing != null && t.getId().equals(existing.getCustomerTypeId())))
                .orElseThrow(() -> new ValidationException("customerTypeId", "Choose an active customer type."));
        if (r.deliveryDay() < 1 || r.deliveryDay() > 7) {
            throw new ValidationException("deliveryDay", "Choose the delivery day.");
        }
        if (r.cycleWeeks() < 1 || r.cycleWeeks() > 4) {
            throw new ValidationException("cycleWeeks", "Choose the cycle (every 1, 2, 3 or 4 weeks).");
        }
        DayOfWeek day = DayOfWeek.of(r.deliveryDay());
        if (r.nextDeliveryDate().getDayOfWeek() != day) {
            throw new ValidationException("nextDeliveryDate", "Next delivery date " + BusinessDates.format(r.nextDeliveryDate())
                    + " is a " + dayName(r.nextDeliveryDate().getDayOfWeek()) + ", but the delivery day is " + dayName(day) + ".");
        }
        BigDecimal limit = Money.ZERO;
        int terms = 0;
        if (r.payType() != PayType.CASH) {
            if (!Money.isPositive(r.creditLimit())) {
                throw new ValidationException("creditLimit", "Credit and Monthly bill customers need a credit limit.");
            }
            if (r.termsDays() == null || !CREDIT_TERMS.contains(r.termsDays())) {
                throw new ValidationException("termsDays", "Choose the payment terms from the list.");
            }
            limit = Money.of(r.creditLimit());
            terms = r.termsDays();
        }
        Map<String, Integer> usual = new LinkedHashMap<>();
        if (r.usualQty() != null) {
            for (Map.Entry<String, Integer> e : r.usualQty().entrySet()) {
                if (bottleTypes.findById(e.getKey()).isEmpty()) {
                    throw new ValidationException("usualQty", "Unknown bottle type " + e.getKey() + ".");
                }
                int q = e.getValue() == null ? 0 : e.getValue();
                if (q < 0) {
                    throw new ValidationException("usualQty." + e.getKey(), "Usual quantity cannot be negative.");
                }
                if (q > 0) {
                    usual.put(e.getKey(), q);
                }
            }
        }
        boolean optIn = Boolean.TRUE.equals(r.whatsappOptIn());
        String wa = Texts.blankToNull(r.whatsappNumber());
        if (optIn && wa == null && phone.startsWith("07")) {
            wa = phone;
        }
        if (wa != null && !PHONE.matcher(wa).matches()) {
            throw new ValidationException("whatsappNumber", MSG_PHONE);
        }
        if (optIn && wa == null) {
            throw new ValidationException("whatsappNumber", "Enter the WhatsApp number (a mobile number).");
        }
        if (!Boolean.TRUE.equals(r.allowDuplicatePhone())) {
            String digits = phone.replaceAll("\\D", "");
            repository.findSamePhone(digits, existing == null ? -1 : existing.getId()).ifPresent(other -> {
                throw new BusinessException(DUP_PHONE, "Phone number already used by " + other.getCode() + " – " + other.getName() + ".",
                        Map.of("customerId", other.getId(), "customerCode", other.getCode()));
            });
        }
        return new Checked(area, type, phone, limit, terms, usual, wa);
    }

    /** Agreed prices on registration: only those that differ from the standard price; a reason is required. */
    private Map<String, BigDecimal> agreedPrices(CustomerRequest r, CustomerType type, LocalDate from) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        if (r.agreedPrices() == null) {
            return out;
        }
        for (Map.Entry<String, BigDecimal> e : r.agreedPrices().entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            if (!Money.isPositive(e.getValue())) {
                throw new ValidationException("agreedPrices." + e.getKey(), "Enter the agreed price per bottle.");
            }
            BigDecimal standard = pricing.standardPrice(type.getId(), e.getKey(), from).orElse(null);
            if (standard == null || Money.of(e.getValue()).compareTo(standard) != 0) {
                out.put(e.getKey(), Money.of(e.getValue()));
            }
        }
        if (!out.isEmpty()) {
            if (!CurrentUser.get().map(u -> u.permissions().contains(Permissions.CUSTOMER_PRICES_EDIT)).orElse(false)) {
                throw new AccessDeniedException("Agreed prices need the Admin");
            }
            if (Texts.blankToNull(r.agreedReason()) == null) {
                throw new ValidationException("agreedReason", "Enter the reason for the agreed price (e.g. about 3,500 bottles a month).");
            }
        }
        return out;
    }

    private static void apply(Customer c, CustomerRequest r, Checked v) {
        c.update(r.name().trim(), r.address().trim(), v.area().getId(), v.phone(), v.type().getId(), r.deliveryDay().shortValue(),
                r.cycleWeeks().shortValue(), r.nextDeliveryDate(), r.payType(), v.creditLimit(), v.termsDays(),
                Texts.blankToNull(r.notes()), Boolean.TRUE.equals(r.whatsappOptIn()), v.whatsappNumber(), v.usual());
    }

    /** "phone: 077 … → 071 …; cycle: 1 → 2 wk; credit limit: Rs. 20,000.00 → Rs. 25,000.00" */
    private List<String> changes(Customer c, CustomerRequest r, Checked v) {
        List<String> out = new ArrayList<>();
        diff(out, "name", c.getName(), r.name().trim());
        diff(out, "address", c.getAddress(), r.address().trim());
        if (!c.getAreaId().equals(v.area().getId())) {
            out.add("area: " + areas.findById(c.getAreaId()).map(Area::getName).orElse("?") + " → " + v.area().getName());
        }
        diff(out, "phone", c.getPhone(), v.phone());
        if (!c.getCustomerTypeId().equals(v.type().getId())) {
            out.add("type: " + types.findById(c.getCustomerTypeId()).map(CustomerType::getName).orElse("?") + " → " + v.type().getName());
        }
        if (c.getDeliveryDay() != r.deliveryDay()) {
            out.add("day: " + dayName(DayOfWeek.of(c.getDeliveryDay())) + " → " + dayName(DayOfWeek.of(r.deliveryDay())));
        }
        if (c.getCycleWeeks() != r.cycleWeeks()) {
            out.add("cycle: " + c.getCycleWeeks() + " → " + r.cycleWeeks() + " wk");
        }
        if (!c.getNextDeliveryDate().equals(r.nextDeliveryDate())) {
            out.add("next delivery: " + BusinessDates.format(c.getNextDeliveryDate()) + " → " + BusinessDates.format(r.nextDeliveryDate()));
        }
        if (!c.getUsualQty().equals(v.usual())) {
            out.add("usual quantity changed");
        }
        if (c.getPayType() != r.payType()) {
            out.add("payment: " + c.getPayType().label() + " → " + r.payType().label());
        }
        if (c.getCreditLimit().compareTo(v.creditLimit()) != 0) {
            out.add("credit limit: " + Money.format(c.getCreditLimit()) + " → " + Money.format(v.creditLimit()));
        }
        if (c.getTermsDays() != v.termsDays()) {
            out.add("terms: " + c.getTermsDays() + " → " + v.termsDays() + " days");
        }
        diff(out, "notes", c.getNotes(), Texts.blankToNull(r.notes()));
        if (c.isWhatsappOptIn() != Boolean.TRUE.equals(r.whatsappOptIn()) || !Objects.equals(c.getWhatsappNumber(), v.whatsappNumber())) {
            out.add("WhatsApp: " + (Boolean.TRUE.equals(r.whatsappOptIn()) ? "on " + v.whatsappNumber() : "off"));
        }
        return out;
    }

    private static void diff(List<String> out, String what, String before, String after) {
        if (!Objects.equals(before, after)) {
            out.add(what + ": " + (before == null ? "–" : before) + " → " + (after == null ? "–" : after));
        }
    }

    static String dayName(DayOfWeek d) {
        return d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private LocalDate today() {
        return BusinessDates.today(clock);
    }
}
