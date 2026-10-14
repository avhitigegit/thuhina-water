package lk.thuhina.water.quotations.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.customers.dto.CustomerDtos.EndCustomerPriceRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.SetCustomerPriceRequest;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.model.CustomerPrice;
import lk.thuhina.water.customers.repository.CustomerPriceRepository;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.customers.service.CustomerPriceService;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.AreaRepository;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqLineRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqSaved;
import lk.thuhina.water.quotations.model.CqLine;
import lk.thuhina.water.quotations.model.CustomerQuotation;
import lk.thuhina.water.quotations.repository.CustomerQuotationRepository;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Kind;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Status;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Via;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer quotation writes (FR-64 – FR-66, BR-20): new / edit draft (QT-2026-001), make a copy, the steps Draft → Sent →
 * Accepted / Not accepted (only the next one), Apply prices (the quoted water prices become the customer's agreed prices from a
 * date, through the M05 agreed-price service) and the link to a customer registered from an accepted quotation. Every change
 * is written to the quotation's history and the audit log.
 */
@Service
public class CustomerQuotationService {

    static final String ENTITY = "Customer quotation";
    public static final String NOT_DRAFT = "QUOTATION_NOT_DRAFT";
    public static final String STEP = "QUOTATION_STEP";
    public static final String PRICES = "QUOTATION_PRICES";

    private final CustomerQuotationRepository repository;
    private final CustomerQuotationQueryService query;
    private final CustomerRepository customers;
    private final CustomerPriceRepository customerPrices;
    private final CustomerPriceService prices;
    private final CustomerTypeRepository customerTypes;
    private final AreaRepository areas;
    private final BottleTypeRepository bottleTypes;
    private final ProductRepository products;
    private final PricingService pricing;
    private final NumberingService numbering;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CustomerQuotationService(CustomerQuotationRepository repository, CustomerQuotationQueryService query, CustomerRepository customers,
                            CustomerPriceRepository customerPrices, CustomerPriceService prices, CustomerTypeRepository customerTypes,
                            AreaRepository areas, BottleTypeRepository bottleTypes, ProductRepository products, PricingService pricing,
                            NumberingService numbering, AuditService audit, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.query = query;
        this.customers = customers;
        this.customerPrices = customerPrices;
        this.prices = prices;
        this.customerTypes = customerTypes;
        this.areas = areas;
        this.bottleTypes = bottleTypes;
        this.products = products;
        this.pricing = pricing;
        this.numbering = numbering;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ new / edit / copy

    @Transactional
    public CqSaved create(CqRequest r) {
        CustomerQuotation source = r.copyOf() == null ? null
                : repository.findById(r.copyOf()).orElseThrow(() -> new NotFoundException("Quotation", r.copyOf()));
        Checked c = check(r);
        CustomerQuotation q = new CustomerQuotation(numbering.next(SequenceNames.CUSTOMER_QUOTATION), source == null ? null : source.getId());
        q.set(c.header());
        q.replaceLines(lines(q, c));
        q = repository.saveAndFlush(q);
        String copy = source == null ? null : "Copy of " + source.getCqNo();
        history(q.getId(), "CREATED", copy);
        audit.log(AuditAction.CREATE, ENTITY, q.getCqNo(), q.getOrgName() + " – " + Money.format(total(q))
                + (source == null ? "" : " (copy of " + source.getCqNo() + ")"));
        return new CqSaved(query.view(q), q.getCqNo() + " saved as Draft");
    }

    @Transactional
    public CqSaved update(long id, CqRequest r) {
        CustomerQuotation q = repository.lockById(id).orElseThrow(() -> new NotFoundException("Quotation", id));
        Versions.check(r.version(), q.getVersion(), "Quotation " + q.getCqNo());
        if (q.getStatus() != Status.DRAFT) {
            throw new BusinessException(NOT_DRAFT, CustomerQuotationRules.MSG_ONLY_DRAFT);
        }
        Checked c = check(r);
        q.set(c.header());
        // The old lines go first: Hibernate would insert the new ones before deleting (line numbers are unique).
        q.replaceLines(List.of());
        repository.saveAndFlush(q);
        q.replaceLines(lines(q, c));
        repository.saveAndFlush(q);
        history(q.getId(), "EDITED", null);
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), q.getOrgName() + " – " + Money.format(total(q)));
        return new CqSaved(query.view(q), "Quotation saved");
    }

    private record Checked(CustomerQuotation.Header header, List<CqLineRequest> lines, Long customerId, LocalDate date) {
    }

    private Checked check(CqRequest r) {
        LocalDate today = BusinessDates.today(clock);
        String dateProblem = CustomerQuotationRules.dateProblem(r.quoteDate(), today);
        if (dateProblem != null) {
            throw new ValidationException("quoteDate", dateProblem);
        }
        Customer customer = null;
        if (r.customerId() != null) {
            customer = customers.findById(r.customerId())
                    .orElseThrow(() -> new ValidationException("customerId", "Select the customer from the list."));
        }
        List<CqLineRequest> raw = r.lines() == null ? List.of() : r.lines().stream()
                .filter(l -> l != null && (Texts.blankToNull(l.kind()) != null && !"OTHER".equals(l.kind()) || Texts.blankToNull(l.description()) != null))
                .toList();
        List<CustomerQuotationRules.Line> ruleLines = raw.stream()
                .map(l -> new CustomerQuotationRules.Line(kind(l.kind()), Texts.blankToNull(l.description()), l.qty(), l.unitPrice())).toList();
        boolean typeChosen = r.customerTypeId() != null && customerTypes.existsById(r.customerTypeId());
        String problem = CustomerQuotationRules.problem(customer != null, r.orgName(), r.quoteDate(), r.validUntil(), typeChosen, ruleLines);
        if (problem != null) {
            throw new ValidationException(field(problem), problem);
        }
        int terms = r.termsDays() == null ? 30 : r.termsDays();
        if (!PaymentTerms.isValid(terms)) {
            throw new ValidationException("termsDays", CustomerQuotationRules.MSG_TERMS);
        }
        if (!Texts.isEmailOrEmpty(r.email())) {
            throw new ValidationException("email", Texts.MSG_EMAIL);
        }
        Set<String> water = new HashSet<>();
        for (CqLineRequest l : raw) {
            if (kind(l.kind()) == Kind.WATER && !water.add(String.valueOf(l.bottleTypeCode()))) {
                throw new ValidationException("lines", "Water for one bottle type can be quoted only once.");
            }
        }
        Long areaId = r.areaId();
        if (areaId != null && !areas.existsById(areaId)) {
            throw new ValidationException("areaId", "Choose the area from the list.");
        }
        CustomerQuotation.Header h;
        String conditions = r.conditions() == null ? CustomerQuotationRules.DEFAULT_CONDITIONS : Texts.blankToNull(r.conditions());
        if (customer != null) {
            String area = areas.findById(customer.getAreaId()).map(a -> a.getName()).orElse("");
            h = new CustomerQuotation.Header(customer.getId(), customer.getName(), Texts.blankToNull(r.contact()),
                    Texts.blankToNull(r.designation()), customer.getAddress() + (area.isEmpty() ? "" : ", " + area), customer.getAreaId(),
                    customer.getPhone(), Texts.blankToNull(r.email()), r.customerTypeId(), r.quoteDate(), r.validUntil(),
                    Texts.blankToNull(r.subject()), Texts.blankToNull(r.deliveryText()), terms, conditions);
        } else {
            h = new CustomerQuotation.Header(null, r.orgName().trim(), Texts.blankToNull(r.contact()), Texts.blankToNull(r.designation()),
                    Texts.blankToNull(r.address()), areaId, Texts.blankToNull(r.phone()), Texts.blankToNull(r.email()), r.customerTypeId(),
                    r.quoteDate(), r.validUntil(), Texts.blankToNull(r.subject()), Texts.blankToNull(r.deliveryText()), terms, conditions);
        }
        return new Checked(h, raw, customer == null ? null : customer.getId(), r.quoteDate());
    }

    private static String field(String problem) {
        return switch (problem) {
            case CustomerQuotationRules.MSG_TO -> "orgName";
            case CustomerQuotationRules.MSG_VALID -> "validUntil";
            case CustomerQuotationRules.MSG_TYPE -> "customerTypeId";
            default -> "lines";
        };
    }

    private static Kind kind(String text) {
        if (text == null || text.isBlank()) {
            return Kind.OTHER;
        }
        try {
            return Kind.valueOf(text.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("lines", "Unknown item " + text + ".");
        }
    }

    /** The lines with the price the customer would pay on the quotation date (shown beside a changed price). */
    private List<CqLine> lines(CustomerQuotation q, Checked c) {
        List<CqLine> out = new ArrayList<>();
        int n = 0;
        for (CqLineRequest l : c.lines()) {
            Kind kind = kind(l.kind());
            String bottle = null;
            String product = null;
            CustomerQuotationQueryService.Priced now = null;
            switch (kind) {
                case WATER, DEPOSIT -> {
                    BottleType b = bottleTypes.findById(String.valueOf(l.bottleTypeCode()).trim())
                            .orElseThrow(() -> new ValidationException("lines", "Unknown bottle type " + l.bottleTypeCode() + "."));
                    bottle = b.getCode();
                    now = kind == Kind.WATER ? query.water(b, c.header().customerTypeId(), c.customerId(), c.date()) : query.deposit(b, c.date());
                }
                case PRODUCT -> {
                    Product p = products.findByCode(String.valueOf(l.productCode()).trim())
                            .orElseThrow(() -> new ValidationException("lines", "Unknown product " + l.productCode() + "."));
                    product = p.getCode();
                    now = query.product(p);
                }
                default -> {
                }
            }
            out.add(new CqLine(q, ++n, kind, bottle, product, l.description().trim(), l.qty(), Texts.blankToNull(l.unit()),
                    Money.of(l.unitPrice()), now == null || now.price() == null ? null : Money.of(now.price()), now != null && now.agreed()));
        }
        return out;
    }

    // ------------------------------------------------------------------ steps (only the next one)

    @Transactional
    public CqSaved send(long id, String viaText, LocalDate date) {
        CustomerQuotation q = locked(id);
        step(q, Status.SENT);
        Via via;
        try {
            via = Via.valueOf(String.valueOf(viaText).trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("via", CustomerQuotationRules.MSG_VIA);
        }
        LocalDate on = date == null ? BusinessDates.today(clock) : date;
        checkStepDate(q, on);
        q.markSent(via, on);
        repository.saveAndFlush(q);
        history(id, "SENT", via.text());
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), "Status → Sent (" + via.label() + ")");
        return new CqSaved(query.view(q), "Marked as sent");
    }

    @Transactional
    public CqSaved accept(long id, LocalDate date) {
        CustomerQuotation q = locked(id);
        step(q, Status.ACCEPTED);
        LocalDate on = date == null ? BusinessDates.today(clock) : date;
        checkStepDate(q, on);
        q.accept(on);
        repository.saveAndFlush(q);
        history(id, "ACCEPTED", null);
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), "Status → Accepted");
        return new CqSaved(query.view(q), "Quotation accepted");
    }

    @Transactional
    public CqSaved reject(long id, String reason) {
        CustomerQuotation q = locked(id);
        step(q, Status.REJECTED);
        String why = Texts.blankToNull(reason);
        if (why == null) {
            throw new ValidationException("reason", CustomerQuotationRules.MSG_REASON);
        }
        q.reject(why);
        repository.saveAndFlush(q);
        history(id, "REJECTED", why);
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), "Status → Not accepted – " + why);
        return new CqSaved(query.view(q), "Marked as not accepted");
    }

    private CustomerQuotation locked(long id) {
        return repository.lockById(id).orElseThrow(() -> new NotFoundException("Quotation", id));
    }

    private static void step(CustomerQuotation q, Status to) {
        String problem = CustomerQuotationRules.stepProblem(q.getStatus(), to);
        if (problem != null) {
            throw new BusinessException(STEP, problem);
        }
    }

    private void checkStepDate(CustomerQuotation q, LocalDate on) {
        String problem = CustomerQuotationRules.dateProblem(on, BusinessDates.today(clock));
        if (problem != null) {
            throw new ValidationException("date", problem);
        }
        if (on.isBefore(q.getQuoteDate())) {
            throw new ValidationException("date", CustomerQuotationRules.MSG_SENT_BEFORE);
        }
    }

    // ------------------------------------------------------------------ prices (FR-66)

    /**
     * The quoted water prices become the customer's agreed prices from {@code from}: a line at the price the customer pays then
     * is skipped; a line at the standard price ends an agreed price; any other price is set as an agreed price linked to the
     * quotation. Bills already saved keep their price.
     */
    @Transactional
    public CqSaved applyPrices(long id, LocalDate from) {
        CustomerQuotation q = locked(id);
        if (q.getStatus() != Status.ACCEPTED) {
            throw new BusinessException(PRICES, CustomerQuotationRules.MSG_ONLY_ACCEPTED);
        }
        if (q.getCustomerId() == null) {
            throw new BusinessException(PRICES, CustomerQuotationRules.MSG_LINK_FIRST);
        }
        if (q.getPricesAppliedOn() != null) {
            throw new BusinessException(PRICES, CustomerQuotationRules.MSG_APPLIED);
        }
        List<CqLine> water = q.getLines().stream().filter(l -> l.getKind() == Kind.WATER && l.getUnitPrice().signum() > 0).toList();
        if (water.isEmpty()) {
            throw new BusinessException(PRICES, CustomerQuotationRules.MSG_NO_WATER);
        }
        LocalDate on = from == null ? BusinessDates.today(clock) : from;
        Customer c = customers.findById(q.getCustomerId()).orElseThrow(() -> new NotFoundException("Customer", q.getCustomerId()));
        String reason = trim("Quotation " + q.getCqNo() + " accepted" + (q.getSubject() == null ? "" : " – " + q.getSubject()), 250);
        int n = 0;
        for (CqLine l : water) {
            String code = l.getBottleTypeCode();
            BigDecimal price = Money.of(l.getUnitPrice());
            BigDecimal now = pricing.waterPrice(c.getId(), code, on).map(PricingService.WaterPrice::price).orElse(null);
            boolean later = laterAgreement(c.getId(), code, on);
            if (now != null && now.compareTo(price) == 0 && !later) {
                continue;
            }
            BigDecimal standard = pricing.standardPrice(c.getCustomerTypeId(), code, on).orElse(null);
            if (standard != null && standard.compareTo(price) == 0) {
                if (pricing.isAgreed(c.getId(), code, on) || later) {
                    prices.end(c.getId(), new EndCustomerPriceRequest(code, on, trim("Quotation " + q.getCqNo() + " accepted at the standard price", 250)));
                    n++;
                }
                continue;
            }
            prices.set(c.getId(), new SetCustomerPriceRequest(code, price, on, null, reason, q.getId()));
            n++;
        }
        q.pricesApplied(on);
        repository.saveAndFlush(q);
        String note = n + " price" + (n == 1 ? "" : "s") + " for " + c.getCode() + " from " + BusinessDates.format(on);
        history(id, "PRICES_APPLIED", note);
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), "Prices applied – " + note);
        return new CqSaved(query.view(q), "Agreed prices updated");
    }

    private boolean laterAgreement(long customerId, String code, LocalDate on) {
        for (CustomerPrice p : customerPrices.findByCustomerIdAndBottleTypeCodeOrderByValidFromAscIdAsc(customerId, code)) {
            if (!p.isCancelled() && p.getValidFrom().isAfter(on)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ registration from an accepted quotation

    /** Before registering: the quotation must be accepted and not yet linked to a customer. */
    @Transactional(readOnly = true)
    public CustomerQuotation registrable(long id) {
        CustomerQuotation q = repository.findById(id).orElseThrow(() -> new NotFoundException("Quotation", id));
        if (q.getStatus() != Status.ACCEPTED || q.getCustomerId() != null) {
            throw new ValidationException("fromQuotationId", CustomerQuotationRules.MSG_NOT_REGISTRABLE);
        }
        return q;
    }

    /**
     * After the customer is registered from the quotation (with the quoted water prices as agreed prices from the registration
     * date): link the customer and record that the prices are applied.
     */
    @Transactional
    public void registered(long id, long customerId, String customerCode, String customerName, LocalDate from, int agreedPrices) {
        CustomerQuotation q = locked(id);
        q.linkCustomer(customerId);
        boolean hasWater = q.getLines().stream().anyMatch(l -> l.getKind() == Kind.WATER);
        if (hasWater) {
            q.pricesApplied(from);
        }
        repository.saveAndFlush(q);
        history(id, "CUSTOMER_LINKED", customerCode + " – " + customerName);
        if (hasWater) {
            history(id, "PRICES_APPLIED", agreedPrices + " price" + (agreedPrices == 1 ? "" : "s") + " for " + customerCode + " from "
                    + BusinessDates.format(from));
        }
        audit.log(AuditAction.UPDATE, ENTITY, q.getCqNo(), "Registered as customer " + customerCode + " – " + customerName);
    }

    // ------------------------------------------------------------------ helpers

    private void history(long quotationId, String event, String note) {
        jdbc.update("insert into cq_history (quotation_id, event, note, ts, username) values (:q, :e, :n, :ts, :u)",
                new MapSqlParameterSource("q", quotationId).addValue("e", event).addValue("n", note)
                        .addValue("ts", Timestamp.from(clock.instant())).addValue("u", CurrentUser.usernameOrSystem()));
    }

    private static BigDecimal total(CustomerQuotation q) {
        return q.getLines().stream().map(l -> CustomerQuotationRules.amount(l.getQty(), l.getUnitPrice())).reduce(Money.ZERO, BigDecimal::add);
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
