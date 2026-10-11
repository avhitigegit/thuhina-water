package lk.thuhina.water.customers.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerPriceRow;
import lk.thuhina.water.customers.dto.CustomerDtos.EndCustomerPriceRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.SetCustomerPriceRequest;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.model.CustomerPrice;
import lk.thuhina.water.customers.model.CustomerPrice.State;
import lk.thuhina.water.customers.repository.CustomerPriceRepository;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agreed water prices per customer and bottle type (FR-05, BR-08, design 5.5). At most one agreement covers any day:
 * a new one closes the agreement running into its start the day before, and cancels agreements that start on or after
 * it (within its period). Every change is audited with the old and new price and the standard price.
 */
@Service
public class CustomerPriceService {

    static final String ENTITY = "Customer price";
    public static final String NO_AGREED_PRICE = "NO_AGREED_PRICE";

    private final CustomerPriceRepository repository;
    private final CustomerRepository customers;
    private final BottleTypeRepository bottleTypes;
    private final CustomerTypeRepository customerTypes;
    private final AppUserRepository users;
    private final PricingService pricing;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public CustomerPriceService(CustomerPriceRepository repository, CustomerRepository customers, BottleTypeRepository bottleTypes,
                                CustomerTypeRepository customerTypes, AppUserRepository users, PricingService pricing,
                                NumberingService numbering, AuditService audit, Clock clock) {
        this.repository = repository;
        this.customers = customers;
        this.bottleTypes = bottleTypes;
        this.customerTypes = customerTypes;
        this.users = users;
        this.pricing = pricing;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    /** Agreed price from {@code from} (optionally until {@code until}); audited "Agreed price Rs. 300.00 → Rs. 270.00 …". */
    @Transactional
    public CustomerPriceRow set(long customerId, SetCustomerPriceRequest r) {
        Customer c = customer(customerId);
        BottleType bottle = bottleTypes.findById(r.bottleTypeCode().trim()).filter(BottleType::isActive)
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose an active bottle type."));
        if (!Money.isPositive(r.price()) || Money.of(r.price()).compareTo(r.price()) != 0) {
            throw new ValidationException("price", "Enter the agreed price per bottle.");
        }
        if (r.until() != null && r.until().isBefore(r.from())) {
            throw new ValidationException("until", "The end date cannot be before the start date.");
        }
        BigDecimal price = Money.of(r.price());
        BigDecimal before = pricing.waterPrice(customerId, bottle.getCode(), r.from()).map(PricingService.WaterPrice::price).orElse(null);
        BigDecimal standard = pricing.standardPrice(c.getCustomerTypeId(), bottle.getCode(), r.from()).orElse(null);

        for (CustomerPrice e : repository.findByCustomerIdAndBottleTypeCodeOrderByValidFromAscIdAsc(customerId, bottle.getCode())) {
            if (e.isCancelled()) {
                continue;
            }
            if (!e.getValidFrom().isBefore(r.from())) {
                if (r.until() == null || !e.getValidFrom().isAfter(r.until())) {
                    e.cancel("Replaced by a new agreed price from " + BusinessDates.format(r.from()));
                }
            } else if (e.getValidUntil() == null || !e.getValidUntil().isBefore(r.from())) {
                e.closeOn(r.from().minusDays(1));
            }
        }
        String reason = r.reason().trim();
        CustomerPrice saved = repository.saveAndFlush(new CustomerPrice(numbering.next(SequenceNames.CUSTOMER_PRICE), customerId,
                bottle.getCode(), price, standard, r.from(), r.until(), reason, r.quotationId()));
        audit.log(AuditAction.UPDATE, ENTITY, c.getCode() + " " + label(bottle), "Agreed price " + money(before) + " → "
                + Money.format(price) + " from " + BusinessDates.format(r.from())
                + (r.until() == null ? "" : " until " + BusinessDates.format(r.until()))
                + " (standard " + money(standard) + ") – " + reason);
        return row(saved, c, newContext(), today());
    }

    /** Back to the standard price from {@code from}: later agreements are cancelled, the running one is closed. */
    @Transactional
    public void end(long customerId, EndCustomerPriceRequest r) {
        Customer c = customer(customerId);
        BottleType bottle = bottleTypes.findById(r.bottleTypeCode().trim())
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose the bottle type."));
        String reason = r.reason().trim();
        int changed = 0;
        for (CustomerPrice e : repository.findByCustomerIdAndBottleTypeCodeOrderByValidFromAscIdAsc(customerId, bottle.getCode())) {
            if (e.isCancelled()) {
                continue;
            }
            if (!e.getValidFrom().isBefore(r.from())) {
                e.cancel(reason);
                changed++;
            } else if (e.getValidUntil() == null || !e.getValidUntil().isBefore(r.from())) {
                e.closeOn(r.from().minusDays(1));
                changed++;
            }
        }
        if (changed == 0) {
            throw new BusinessException(NO_AGREED_PRICE, c.getName() + " has no agreed " + label(bottle) + " price on or after "
                    + BusinessDates.format(r.from()) + ".");
        }
        repository.flush();
        audit.log(AuditAction.UPDATE, ENTITY, c.getCode() + " " + label(bottle),
                "Back to the standard price from " + BusinessDates.format(r.from()) + " – " + reason);
    }

    /** One customer's agreements, newest start first, with their state today (Prices tab). */
    @Transactional(readOnly = true)
    public List<CustomerPriceRow> history(long customerId) {
        Customer c = customer(customerId);
        Context ctx = newContext();
        LocalDate today = today();
        List<CustomerPrice> rows = repository.findByCustomerIdOrderByValidFromDescIdDesc(customerId);
        ctx.addAll(rows);
        return rows.stream().map(e -> row(e, c, ctx, today)).toList();
    }

    /**
     * All customers' agreements for Bottles &amp; Products → Prices: {@code all = false} = in force and scheduled only.
     * Sorted by customer name, then newest start first.
     */
    @Transactional(readOnly = true)
    public List<CustomerPriceRow> list(boolean all) {
        LocalDate today = today();
        List<CustomerPrice> rows = repository.findAllByOrderByValidFromDescIdDesc();
        Map<Long, Customer> byId = customers.findByIdIn(rows.stream().map(CustomerPrice::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getId, x -> x));
        Context ctx = newContext();
        ctx.addAll(rows);
        return rows.stream()
                .filter(e -> all || (!e.isCancelled() && (e.getValidUntil() == null || !e.getValidUntil().isBefore(today))))
                .sorted(Comparator.comparing((CustomerPrice e) -> byId.get(e.getCustomerId()).getName().toLowerCase())
                        .thenComparing(CustomerPrice::getValidFrom, Comparator.reverseOrder()))
                .map(e -> row(e, byId.get(e.getCustomerId()), ctx, today))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    /** In force / scheduled / ended / replaced / cancelled as of {@code today}. */
    static State state(CustomerPrice e, List<CustomerPrice> sameKey, LocalDate today) {
        if (e.isCancelled()) {
            return State.CANCELLED;
        }
        if (e.getValidFrom().isAfter(today)) {
            return State.SCHEDULED;
        }
        if (e.getValidUntil() != null && e.getValidUntil().isBefore(today)) {
            LocalDate next = e.getValidUntil().plusDays(1);
            boolean followed = sameKey.stream().anyMatch(o -> o != e && !o.isCancelled() && o.getValidFrom().equals(next));
            return followed ? State.REPLACED : State.ENDED;
        }
        return State.IN_FORCE;
    }

    /** Names, types and agreements needed to build rows. */
    private final class Context {
        final Map<Long, String> typeNames = new HashMap<>();
        final Map<String, BottleType> bottles = new HashMap<>();
        final Map<String, String> userNames = new HashMap<>();
        final Map<String, List<CustomerPrice>> byKey = new HashMap<>();

        void addAll(List<CustomerPrice> rows) {
            rows.forEach(e -> byKey.computeIfAbsent(e.getCustomerId() + "|" + e.getBottleTypeCode(), k -> new ArrayList<>()).add(e));
            Set<String> who = new HashSet<>();
            rows.forEach(e -> who.add(e.getCreatedBy()));
            users.findByUsernameIn(who).forEach(u -> userNames.put(u.getUsername(), u.getFullName()));
        }
    }

    private Context newContext() {
        Context ctx = new Context();
        customerTypes.findAll().forEach(t -> ctx.typeNames.put(t.getId(), t.getName()));
        bottleTypes.findAll().forEach(b -> ctx.bottles.put(b.getCode(), b));
        return ctx;
    }

    private CustomerPriceRow row(CustomerPrice e, Customer c, Context ctx, LocalDate today) {
        BottleType b = ctx.bottles.get(e.getBottleTypeCode());
        if (b == null) {
            b = bottleTypes.findById(e.getBottleTypeCode()).orElseThrow();
        }
        List<CustomerPrice> same = ctx.byKey.getOrDefault(e.getCustomerId() + "|" + e.getBottleTypeCode(), List.of(e));
        BigDecimal standardNow = pricing.standardPrice(c.getCustomerTypeId(), e.getBottleTypeCode(), today).orElse(null);
        int usual = c.getUsualQty().getOrDefault(e.getBottleTypeCode(), 0);
        return new CustomerPriceRow(e.getId(), e.getCpNo(), c.getId(), c.getCode(), c.getName(),
                ctx.typeNames.getOrDefault(c.getCustomerTypeId(), ""), b.getCode(), label(b), e.getPrice(), e.getStandardAtSet(),
                standardNow, e.getValidFrom(), e.getValidUntil(), e.getReason(), e.getCustomerQuotationId(),
                state(e, same, today).name(), e.getCancelReason(), e.getCreatedBy(), ctx.userNames.get(e.getCreatedBy()),
                ScheduleRules.bottlesPerMonth(usual, c.getCycleWeeks()));
    }

    private Customer customer(long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    private LocalDate today() {
        return BusinessDates.today(clock);
    }

    private static String label(BottleType b) {
        return LedgerRules.bottleLabel(b.getLitres());
    }

    private static String money(BigDecimal v) {
        return v == null ? "none" : Money.format(v);
    }
}
