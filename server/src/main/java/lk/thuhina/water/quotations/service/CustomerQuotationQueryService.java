package lk.thuhina.water.quotations.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.printing.CompanyHeader;
import lk.thuhina.water.printing.CompanyHeaderService;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqHistoryRow;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqItemOption;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqKpis;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqLineView;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqListResponse;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqListRow;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqView;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqWaterPrice;
import lk.thuhina.water.quotations.model.CqLine;
import lk.thuhina.water.quotations.model.CustomerQuotation;
import lk.thuhina.water.quotations.repository.CustomerQuotationRepository;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Kind;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Status;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Via;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer quotation reads (design 6 Customer quotations): the items that can be quoted with today's prices, the list with
 * KPIs and the next step, and one quotation with its lines, history, the buttons its step allows, the email text and the
 * Register-as-customer pre-fill.
 */
@Service
@Transactional(readOnly = true)
public class CustomerQuotationQueryService {

    private final CustomerQuotationRepository repository;
    private final BottleTypeRepository bottleTypes;
    private final ProductRepository products;
    private final PricingService pricing;
    private final CompanyHeaderService company;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public CustomerQuotationQueryService(CustomerQuotationRepository repository, BottleTypeRepository bottleTypes, ProductRepository products,
                                 PricingService pricing, CompanyHeaderService company, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository = repository;
        this.bottleTypes = bottleTypes;
        this.products = products;
        this.pricing = pricing;
        this.company = company;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ items

    /** A line's description, unit and the price on {@code date}, for one item. */
    public record Priced(String description, String unit, BigDecimal price, boolean agreed) {
    }

    /**
     * What can be quoted with the price on {@code date}: for an existing customer their price now (agreed or standard),
     * otherwise the standard price of the customer type (prototype itemInfo).
     */
    public List<CqItemOption> items(Long customerTypeId, Long customerId, LocalDate date) {
        LocalDate on = date == null ? BusinessDates.today(clock) : date;
        List<CqItemOption> out = new ArrayList<>();
        List<BottleType> bottles = bottleTypes.findByActiveTrueOrderByLitresDesc();
        for (BottleType b : bottles) {
            Priced p = water(b, customerTypeId, customerId, on);
            out.add(new CqItemOption("WATER|" + b.getCode(), Kind.WATER.name(), b.getCode(), null, label(b) + " water (refill, delivered)",
                    p.description(), p.unit(), p.price(), p.agreed()));
        }
        for (BottleType b : bottles) {
            Priced p = deposit(b, on);
            out.add(new CqItemOption("DEPOSIT|" + b.getCode(), Kind.DEPOSIT.name(), b.getCode(), null, label(b) + " bottle deposit",
                    p.description(), p.unit(), p.price(), false));
        }
        for (Product pr : products.findAllByOrderByCodeAsc()) {
            if (pr.isActive()) {
                out.add(new CqItemOption("PRODUCT|" + pr.getCode(), Kind.PRODUCT.name(), null, pr.getCode(), pr.getName(), pr.getName(),
                        "units", Money.of(pr.getSellingPrice()), false));
            }
        }
        out.add(new CqItemOption("OTHER", Kind.OTHER.name(), null, null, "Other (type a description)", "", "", null, false));
        return out;
    }

    Priced water(BottleType b, Long customerTypeId, Long customerId, LocalDate on) {
        String description = b.getName() + " – drinking water (refill, delivered)";
        if (customerId != null) {
            Optional<PricingService.WaterPrice> w = pricing.waterPrice(customerId, b.getCode(), on);
            return new Priced(description, "per month", w.map(PricingService.WaterPrice::price).orElse(null),
                    w.map(PricingService.WaterPrice::agreed).orElse(false));
        }
        BigDecimal std = customerTypeId == null ? null : pricing.standardPrice(customerTypeId, b.getCode(), on).orElse(null);
        return new Priced(description, "per month", std, false);
    }

    Priced deposit(BottleType b, LocalDate on) {
        return new Priced(b.getName() + " – bottle deposit (one time)", "bottles", pricing.deposit(b.getCode(), on).orElse(null), false);
    }

    Priced product(Product p) {
        return new Priced(p.getName(), "units", Money.of(p.getSellingPrice()), false);
    }

    static String label(BottleType b) {
        return LedgerRules.bottleLabel(b.getLitres());
    }

    // ------------------------------------------------------------------ list

    private record Head(long id, String cqNo, LocalDate quoteDate, String orgName, Long customerId, String customerCode, String contact,
                        String designation, String subject, String typeName, LocalDate validUntil, Status status, Via via,
                        LocalDate pricesApplied, int lines, boolean hasWater, BigDecimal total) {
    }

    /** Every quotation, newest first, filtered by the shown status (incl. EXPIRED) and a search; KPIs over all of them. */
    public CqListResponse list(String status, String q) {
        LocalDate today = BusinessDates.today(clock);
        List<Head> all = jdbc.query("select q.id, q.cq_no, q.quote_date, q.org_name, q.customer_id, c.code as customer_code, q.contact, "
                        + "q.designation, q.subject, t.name as type_name, q.valid_until, q.status, q.sent_via, q.prices_applied_on, "
                        + "(select count(*) from cq_line l where l.quotation_id = q.id) as lines, "
                        + "exists (select 1 from cq_line l where l.quotation_id = q.id and l.item_kind = 'WATER') as has_water, "
                        + "(select coalesce(sum(l.qty * l.unit_price), 0) from cq_line l where l.quotation_id = q.id) as total "
                        + "from customer_quotation q join customer_type t on t.id = q.customer_type_id "
                        + "left join customer c on c.id = q.customer_id order by q.id desc", Map.of(),
                (rs, i) -> new Head(rs.getLong("id"), rs.getString("cq_no"), rs.getObject("quote_date", LocalDate.class),
                        rs.getString("org_name"), (Long) rs.getObject("customer_id"), rs.getString("customer_code"), rs.getString("contact"),
                        rs.getString("designation"), rs.getString("subject"), rs.getString("type_name"),
                        rs.getObject("valid_until", LocalDate.class), Status.valueOf(rs.getString("status")),
                        rs.getString("sent_via") == null ? null : Via.valueOf(rs.getString("sent_via")),
                        rs.getObject("prices_applied_on", LocalDate.class), rs.getInt("lines"), rs.getBoolean("has_water"),
                        Money.of(rs.getBigDecimal("total"))));
        int waiting = 0;
        int drafts = 0;
        int accepted = 0;
        int closed = 0;
        BigDecimal waitingTotal = Money.ZERO;
        BigDecimal acceptedTotal = Money.ZERO;
        String search = Texts.blankToNull(q) == null ? null : q.trim().toLowerCase();
        String wanted = Texts.blankToNull(status);
        List<CqListRow> rows = new ArrayList<>();
        for (Head h : all) {
            String shown = CustomerQuotationRules.shown(h.status(), h.validUntil(), today);
            switch (shown) {
                case "SENT" -> {
                    waiting++;
                    waitingTotal = waitingTotal.add(h.total());
                }
                case "DRAFT" -> drafts++;
                case "ACCEPTED" -> {
                    accepted++;
                    acceptedTotal = acceptedTotal.add(h.total());
                }
                default -> closed++;
            }
            if (wanted != null && !wanted.equalsIgnoreCase(shown)) {
                continue;
            }
            if (search != null && !String.join(" ", h.cqNo(), h.orgName(), nz(h.contact()), nz(h.subject()), nz(h.customerCode()))
                    .toLowerCase().contains(search)) {
                continue;
            }
            rows.add(new CqListRow(h.id(), h.cqNo(), h.quoteDate(), h.orgName(), h.customerId(), h.customerCode(), h.contact(),
                    h.designation(), h.subject(), h.lines(), h.typeName(), h.total(), h.validUntil(), h.status().name(), shown,
                    CustomerQuotationRules.shownLabel(shown), h.status() == Status.SENT && h.via() != null ? h.via().text() : null,
                    CustomerQuotationRules.nextStep(shown, h.customerId() != null, h.pricesApplied() != null, h.hasWater())));
        }
        return new CqListResponse(rows, new CqKpis(waiting, waitingTotal, drafts, accepted, acceptedTotal, closed));
    }

    // ------------------------------------------------------------------ one quotation

    public CqView view(long id) {
        CustomerQuotation q = repository.findById(id).orElseThrow(() -> new NotFoundException("Quotation", id));
        return view(q);
    }

    public CqView view(CustomerQuotation q) {
        LocalDate today = BusinessDates.today(clock);
        Map<String, Object> h = jdbc.queryForMap("select q.org_name, q.contact, q.designation, q.address, q.area_id, a.name as area_name, "
                        + "q.phone, q.email, q.customer_type_id, t.name as type_name, q.valid_until, q.delivery_text, q.terms_days, "
                        + "q.conditions, q.sent_via, q.sent_on, q.accepted_on, q.reject_reason, c.code as customer_code, "
                        + "cp.cq_no as copy_of_no, q.created_by "
                        + "from customer_quotation q join customer_type t on t.id = q.customer_type_id "
                        + "left join area a on a.id = q.area_id left join customer c on c.id = q.customer_id "
                        + "left join customer_quotation cp on cp.id = q.copy_of_id where q.id = :id",
                Map.of("id", q.getId()));
        Status status = q.getStatus();
        LocalDate validUntil = toDate(h.get("valid_until"));
        String shown = CustomerQuotationRules.shown(status, validUntil, today);
        Via via = h.get("sent_via") == null ? null : Via.valueOf((String) h.get("sent_via"));

        List<CqLineView> lines = new ArrayList<>();
        BigDecimal total = Money.ZERO;
        boolean hasWater = false;
        for (CqLine l : q.getLines()) {
            BigDecimal amount = Money.of(CustomerQuotationRules.amount(l.getQty(), l.getUnitPrice()));
            total = total.add(amount);
            hasWater |= l.getKind() == Kind.WATER;
            boolean below = l.getCurrentPrice() != null && l.getUnitPrice().compareTo(l.getCurrentPrice()) < 0;
            lines.add(new CqLineView(l.getLineNo(), l.getKind().name(), l.getBottleTypeCode(), l.getProductCode(), l.getDescription(),
                    l.getQty(), l.getUnit(), Money.of(l.getUnitPrice()), amount, l.getCurrentPrice() == null ? null : Money.of(l.getCurrentPrice()),
                    l.isCurrentAgreed(), below));
        }
        Long customerId = q.getCustomerId();
        String contact = (String) h.get("contact");
        String email = (String) h.get("email");
        int terms = ((Number) h.get("terms_days")).intValue();
        String createdBy = (String) h.get("created_by");
        List<CqHistoryRow> history = history(q.getId());
        Map<String, String> names = userNames(List.of(createdBy));

        boolean accepted = status == Status.ACCEPTED;
        String typeName = (String) h.get("type_name");
        long typeId = ((Number) h.get("customer_type_id")).longValue();
        List<CqWaterPrice> agreed = new ArrayList<>();
        Map<String, BottleType> bottles = new HashMap<>();
        bottleTypes.findAllByOrderByLitresDesc().forEach(b -> bottles.put(b.getCode(), b));
        for (CqLine l : q.getLines()) {
            if (l.getKind() != Kind.WATER || l.getUnitPrice().signum() <= 0) {
                continue;
            }
            BigDecimal std = pricing.standardPrice(typeId, l.getBottleTypeCode(), today).orElse(null);
            if (std == null || std.compareTo(l.getUnitPrice()) != 0) {
                BottleType b = bottles.get(l.getBottleTypeCode());
                agreed.add(new CqWaterPrice(l.getBottleTypeCode(), b == null ? l.getBottleTypeCode() : label(b), Money.of(l.getUnitPrice()), std));
            }
        }
        String registerNotes = "From quotation " + q.getCqNo() + (contact == null ? "" : " · contact " + contact) + (email == null ? "" : " · " + email);

        return new CqView(q.getId(), q.getCqNo(), customerId, (String) h.get("customer_code"), q.getOrgName(), contact,
                (String) h.get("designation"), (String) h.get("address"), toLong(h.get("area_id")), (String) h.get("area_name"),
                (String) h.get("phone"), email, typeId, typeName, q.getQuoteDate(), validUntil, q.getSubject(), (String) h.get("delivery_text"),
                terms, PaymentTerms.label(terms), (String) h.get("conditions"), status.name(), shown, CustomerQuotationRules.shownLabel(shown),
                via == null ? null : via.name(), via == null ? null : via.text(), toDate(h.get("sent_on")), toDate(h.get("accepted_on")),
                (String) h.get("reject_reason"), q.getPricesAppliedOn(), (String) h.get("copy_of_no"), lines, total, history, hasWater,
                status == Status.DRAFT, status == Status.DRAFT, status == Status.SENT, accepted && customerId == null,
                accepted && customerId != null && q.getPricesAppliedOn() == null && hasWater,
                emailSubject(q), emailBody(q, contact, lines, total, validUntil, terms), agreed, registerNotes,
                names.get(createdBy), q.getVersion());
    }

    private List<CqHistoryRow> history(long quotationId) {
        record Raw(java.time.Instant at, String event, String note, String user) {
        }
        List<Raw> raw = jdbc.query("select ts, event, note, username from cq_history where quotation_id = :q order by ts, id",
                Map.of("q", quotationId), (rs, i) -> new Raw(rs.getTimestamp("ts").toInstant(), rs.getString("event"), rs.getString("note"),
                        rs.getString("username")));
        Map<String, String> names = userNames(raw.stream().map(Raw::user).toList());
        return raw.stream().map(r -> new CqHistoryRow(r.at(), r.event(), eventLabel(r.event()), r.note(), names.get(r.user()))).toList();
    }

    /** History labels as in the prototype. */
    static String eventLabel(String event) {
        return switch (event) {
            case "CREATED" -> "Draft";
            case "EDITED" -> "Edited";
            case "SENT" -> "Sent";
            case "ACCEPTED" -> "Accepted";
            case "REJECTED" -> "Not accepted";
            case "CUSTOMER_LINKED" -> "Customer registered";
            case "PRICES_APPLIED" -> "Prices applied";
            default -> event;
        };
    }

    // ------------------------------------------------------------------ email (FR-65: the email program opens with the message)

    private String emailSubject(CustomerQuotation q) {
        return "Quotation " + q.getCqNo() + " – " + company.current().name();
    }

    private String emailBody(CustomerQuotation q, String contact, List<CqLineView> lines, BigDecimal total, LocalDate validUntil, int terms) {
        CompanyHeader co = company.current();
        StringBuilder b = new StringBuilder();
        b.append("Dear ").append(contact == null ? "Sir/Madam" : contact).append(",\n\n");
        b.append("Please find attached our quotation ").append(q.getCqNo()).append(" dated ").append(BusinessDates.format(q.getQuoteDate()))
                .append(q.getSubject() == null ? "" : " for " + q.getSubject()).append(".\n\n");
        for (CqLineView l : lines) {
            b.append("- ").append(l.description()).append(": ").append(l.qty()).append(l.unit() == null ? "" : " " + l.unit())
                    .append(" × ").append(Money.format(l.unitPrice())).append(" = ").append(Money.format(l.amount())).append('\n');
        }
        b.append("\nTotal: ").append(Money.format(total)).append("\nValid until: ").append(BusinessDates.format(validUntil))
                .append("\nPayment terms: ").append(PaymentTerms.label(terms)).append("\n\n");
        if (co.phone() != null) {
            b.append("Please contact us on ").append(co.phone()).append(" for any questions.\n\n");
        }
        b.append("Thank you,\n").append(CurrentUser.get().map(AuthUser::fullName).orElse("")).append('\n').append(co.name());
        return b.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static LocalDate toDate(Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) o;
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    /** Full names of users; usernames without a user (seed, system) are shown as they are. */
    Map<String, String> userNames(Collection<String> usernames) {
        Set<String> wanted = new HashSet<>(usernames);
        wanted.remove(null);
        Map<String, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted),
                    rs -> {
                        out.put(rs.getString("username"), rs.getString("full_name"));
                    });
        }
        wanted.forEach(u -> out.putIfAbsent(u, u));
        return out;
    }
}
