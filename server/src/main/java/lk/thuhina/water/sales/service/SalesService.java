package lk.thuhina.water.sales.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.billing.rules.PaymentRules;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.inventory.model.DamageRecord;
import lk.thuhina.water.inventory.repository.DamageRecordRepository;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.OldBottleBrand;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.repository.OldBottleBrandRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.sales.dto.SalesDtos.BillLineRequest;
import lk.thuhina.water.sales.dto.SalesDtos.BillRequest;
import lk.thuhina.water.sales.dto.SalesDtos.BottlesAfter;
import lk.thuhina.water.sales.dto.SalesDtos.CollectEmptiesRequest;
import lk.thuhina.water.sales.dto.SalesDtos.CreditView;
import lk.thuhina.water.sales.dto.SalesDtos.CustomerDamageRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ExtraOrderRequest;
import lk.thuhina.water.sales.dto.SalesDtos.FirstPurchaseRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ProductItemRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ProductSaleRequest;
import lk.thuhina.water.sales.dto.SalesDtos.RegistrationPreview;
import lk.thuhina.water.sales.dto.SalesDtos.ReverseRequest;
import lk.thuhina.water.sales.dto.SalesDtos.SalePreview;
import lk.thuhina.water.sales.dto.SalesDtos.SaleResult;
import lk.thuhina.water.sales.dto.SalesDtos.ScheduleChange;
import lk.thuhina.water.sales.dto.SalesDtos.SettleOwedRequest;
import lk.thuhina.water.sales.dto.SalesDtos.TxnDetail;
import lk.thuhina.water.sales.model.DamageMode;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.model.SalesTxn;
import lk.thuhina.water.sales.model.SalesTxnLine;
import lk.thuhina.water.sales.model.SettleMethod;
import lk.thuhina.water.sales.repository.SalesTxnLineRepository;
import lk.thuhina.water.sales.repository.SalesTxnRepository;
import lk.thuhina.water.sales.rules.Bottles;
import lk.thuhina.water.sales.rules.CreditCheck;
import lk.thuhina.water.sales.rules.CustomerDamageRules;
import lk.thuhina.water.sales.rules.ExchangeRules;
import lk.thuhina.water.sales.rules.ExtraOrderRules;
import lk.thuhina.water.sales.rules.FirstPurchaseRules;
import lk.thuhina.water.sales.rules.OwedSettlementRules;
import lk.thuhina.water.sales.rules.Problem;
import lk.thuhina.water.sales.rules.ProductSaleRules;
import lk.thuhina.water.sales.rules.SaleCalc;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.BrandInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;
import lk.thuhina.water.sales.rules.SaleInputs.ProductInfo;
import lk.thuhina.water.sales.rules.SaleLine;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Permissions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales transactions (M06, design 5.1, 5.2, 5.6). Every sale follows one flow in one database transaction:
 * validate → calculate with the rule class → credit check → save the bill and its lines → Ledger post (stock and
 * customer bottles) → account balance {@code + amount − paid} → audit → next delivery date (delivery bills and first
 * purchases). Previews run the same calculation and checks without saving.
 */
@Service
public class SalesService {

    public static final String DUPLICATE_PAPER_BILL = "DUPLICATE_PAPER_BILL";
    public static final String CUSTOMER_INACTIVE = "CUSTOMER_INACTIVE";
    public static final String NOT_REVERSIBLE = "NOT_REVERSIBLE";
    public static final String ALREADY_REVERSED = "ALREADY_REVERSED";
    public static final String MSG_ONLY_ADMIN = "Only the Admin can approve a sale over the credit limit.";

    private final SalesTxnRepository txns;
    private final SalesTxnLineRepository txnLines;
    private final SalesQueryService queries;
    private final CustomerRepository customers;
    private final CustomerTypeRepository customerTypes;
    private final BottleTypeRepository bottleTypes;
    private final OldBottleBrandRepository brands;
    private final ProductRepository products;
    private final DamageRecordRepository damages;
    private final PricingService pricing;
    private final LedgerService ledger;
    private final AccountService accounts;
    private final NumberingService numbering;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SalesService(SalesTxnRepository txns, SalesTxnLineRepository txnLines, SalesQueryService queries, CustomerRepository customers,
                        CustomerTypeRepository customerTypes, BottleTypeRepository bottleTypes, OldBottleBrandRepository brands,
                        ProductRepository products, DamageRecordRepository damages, PricingService pricing, LedgerService ledger,
                        AccountService accounts, NumberingService numbering, AuditService audit, NamedParameterJdbcTemplate jdbc,
                        ApplicationEventPublisher events, Clock clock) {
        this.txns = txns;
        this.txnLines = txnLines;
        this.queries = queries;
        this.customers = customers;
        this.customerTypes = customerTypes;
        this.bottleTypes = bottleTypes;
        this.brands = brands;
        this.products = products;
        this.damages = damages;
        this.pricing = pricing;
        this.ledger = ledger;
        this.accounts = accounts;
        this.numbering = numbering;
        this.audit = audit;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    // ================================================================== delivery bills (exchange / left at door)

    @Transactional(readOnly = true)
    public SalePreview previewBill(BillRequest r) {
        return previewBill(ctx(r.customerId(), r.date()), r, true);
    }

    @Transactional
    public SaleResult postBill(BillRequest r) {
        return saveBill(ctx(r.customerId(), r.date()), r, true, r.approveOverLimit());
    }

    /**
     * My Deliveries on the phone (M17): the bill is always for today, the paper bill number is optional (the system bill
     * number is the record) and the delivery staff cannot approve a sale over the credit limit.
     */
    @Transactional(readOnly = true)
    public SalePreview previewMobileBill(BillRequest r) {
        return previewBill(ctx(r.customerId(), today()), r, false);
    }

    @Transactional
    public SaleResult postMobileBill(BillRequest r) {
        return saveBill(ctx(r.customerId(), today()), r, false, false);
    }

    private SalePreview previewBill(Ctx x, BillRequest r, boolean paperRequired) {
        List<Problem> problems = new ArrayList<>(common(x, r.paperBillNo(), paperRequired, r.paid()));
        Problem reference = referenceProblem(r);
        if (reference != null) {
            problems.add(reference);
        }
        return preview(x, billCalc(x, r), problems, r.paid());
    }

    private SaleResult saveBill(Ctx x, BillRequest r, boolean paperRequired, Boolean approveOverLimit) {
        Problem reference = referenceProblem(r);
        if (reference != null) {
            throw toException(reference);
        }
        SaleCalc calc = billCalc(x, r);
        SalesKind kind = billKind(r);
        String details = kind.label() + " for " + x.customer().getName() + ": " + String.join(", ", calc.lines().stream()
                .map(l -> l.getLabel() + " filled " + l.getFilled() + " / empties " + l.getEmpties()).toList()) + ", "
                + Money.format(calc.amount());
        Posted posted = postAndSave(new Spec(kind, x, null, r.paperBillNo(), paperRequired, calc, r.paid(), r.payMethod(),
                Texts.blankToNull(r.payReference()), r.note(), Boolean.TRUE.equals(r.phoneConfirmed()), null, null, approveOverLimit,
                details, true));
        if (kind == SalesKind.LEFT_AT_DOOR) {
            events.publishEvent(new LeftAtDoorBillSaved(posted.txn().getId(), x.customer().getId(), x.date()));
        }
        return result(posted);
    }

    /** Money received by bank transfer or cheque needs its reference (as for payments, FR-50). */
    private static Problem referenceProblem(BillRequest r) {
        if (Money.of(r.paid()).signum() <= 0) {
            return null;
        }
        String problem = PaymentRules.referenceProblem(r.payMethod(), Texts.blankToNull(r.payReference()));
        return problem == null ? null : Problem.field("payReference", problem);
    }

    private static SalesKind billKind(BillRequest r) {
        if (r.kind() == null || r.kind() == SalesKind.EXCHANGE) {
            return SalesKind.EXCHANGE;
        }
        if (r.kind() == SalesKind.LEFT_AT_DOOR) {
            return SalesKind.LEFT_AT_DOOR;
        }
        throw new ValidationException("kind", "A delivery bill is an Exchange or Left at door.");
    }

    private SaleCalc billCalc(Ctx x, BillRequest r) {
        List<ExchangeRules.Line> lines = new ArrayList<>();
        if (r.lines() != null) {
            for (BillLineRequest l : r.lines()) {
                if (l == null || (n(l.filled()) == 0 && n(l.empties()) == 0)) {
                    continue;
                }
                BottleInfo b = bottle(l.bottleTypeCode());
                lines.add(new ExchangeRules.Line(b, n(l.filled()), n(l.empties()), price(x, b.code())));
            }
        }
        return ExchangeRules.calculate(x.view(), billKind(r) == SalesKind.LEFT_AT_DOOR, lines, r.note(), Boolean.TRUE.equals(r.phoneConfirmed()));
    }

    // ================================================================== first purchase

    @Transactional(readOnly = true)
    public SalePreview previewFirstPurchase(FirstPurchaseRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        return preview(x, firstCalc(x, r), common(x, r.paperBillNo(), false, r.paid()), r.paid());
    }

    @Transactional
    public SaleResult firstPurchase(FirstPurchaseRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        SaleCalc calc = firstCalc(x, r);
        return post(new Spec(SalesKind.FIRST_PURCHASE, x, null, r.paperBillNo(), false, calc, r.paid(), null, r.note(), false, null, null,
                r.approveOverLimit(), firstDetails(x.customer(), calc), true));
    }

    private SaleCalc firstCalc(Ctx x, FirstPurchaseRequest r) {
        BottleInfo b = bottle(r.bottleTypeCode());
        return FirstPurchaseRules.calculate(x.view(), b, n(r.qty()), n(r.oldBottles()), brand(r.oldBrandId()), price(x, b.code()),
                deposit(b.code(), x.date()));
    }

    private static String firstDetails(Customer c, SaleCalc calc) {
        SaleLine l = calc.lines().getFirst();
        return "First purchase for " + c.getName() + ": " + l.getFilled() + " × " + l.getLabel() + ", deposits " + l.getDepositCount()
                + ", old bottles " + l.getOldBottles() + ", " + Money.format(calc.amount());
    }

    // ================================================================== extra order

    @Transactional(readOnly = true)
    public SalePreview previewExtraOrder(ExtraOrderRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        return preview(x, extraCalc(x, r), common(x, r.paperBillNo(), false, r.paid()), r.paid());
    }

    @Transactional
    public SaleResult extraOrder(ExtraOrderRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        SaleCalc calc = extraCalc(x, r);
        SaleLine l = calc.lines().getFirst();
        String details = "Extra order for " + x.customer().getName() + ": " + l.getFilled() + " × " + l.getLabel() + ", new bottles "
                + l.getNewBottles() + ", deposits " + l.getDepositCount() + ", " + Money.format(calc.amount());
        return post(new Spec(SalesKind.EXTRA_ORDER, x, null, r.paperBillNo(), false, calc, r.paid(), null, r.note(), false, null, null,
                r.approveOverLimit(), details, false));
    }

    private SaleCalc extraCalc(Ctx x, ExtraOrderRequest r) {
        BottleInfo b = bottle(r.bottleTypeCode());
        return ExtraOrderRules.calculate(x.view(), b, n(r.filled()), n(r.empties()), n(r.oldBottles()), brand(r.oldBrandId()),
                price(x, b.code()), deposit(b.code(), x.date()));
    }

    // ================================================================== collect empties

    @Transactional(readOnly = true)
    public SalePreview previewCollectEmpties(CollectEmptiesRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        return preview(x, ExchangeRules.collect(x.view(), bottle(r.bottleTypeCode()), n(r.qty())),
                common(x, r.paperBillNo(), false, null), null);
    }

    @Transactional
    public SaleResult collectEmpties(CollectEmptiesRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        BottleInfo b = bottle(r.bottleTypeCode());
        SaleCalc calc = ExchangeRules.collect(x.view(), b, n(r.qty()));
        String details = "Collected " + n(r.qty()) + " × " + b.label() + " empties from " + x.customer().getName();
        return post(new Spec(SalesKind.COLLECT_EMPTIES, x, null, r.paperBillNo(), false, calc, null, null, r.note(), false, null, null,
                false, details, false));
    }

    // ================================================================== customer damage

    @Transactional(readOnly = true)
    public SalePreview previewCustomerDamage(CustomerDamageRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        return preview(x, damageCalc(x, r), common(x, r.paperBillNo(), false, r.paid()), r.paid());
    }

    /** BR-06: also creates the damage record (responsibility Customer, "At customer") linked to the bill. */
    @Transactional
    public SaleResult customerDamage(CustomerDamageRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        SaleCalc calc = damageCalc(x, r);
        DamageMode mode = r.mode() == null ? DamageMode.REPLACE : r.mode();
        SaleLine l = calc.lines().getFirst();
        String details = "Customer damage – " + x.customer().getName() + ": " + l.getDamaged() + " × " + l.getLabel()
                + (mode == DamageMode.REDUCE ? ", holding reduced" : ", new bottle charged " + Money.format(calc.amount()));
        String reason = Texts.blankToNull(r.reason());
        String note = reason == null ? null : reason + (Texts.blankToNull(r.note()) == null ? "" : " – " + r.note().trim());
        Posted p = postAndSave(new Spec(SalesKind.CUSTOMER_DAMAGE, x, null, r.paperBillNo(), false, calc, r.paid(), null, note, false,
                mode, null, r.approveOverLimit(), details, false));
        DamageRecord d = damages.save(DamageRecord.customer(numbering.next(SequenceNames.DAMAGE), x.date(), l.getBottleTypeCode(),
                l.getDamaged(), reason, Texts.blankToNull(r.note()), x.customer().getId(), p.txn().getId()));
        audit.log(AuditAction.CREATE, "Damage (customer)", d.getDamageNo(), l.getDamaged() + " × " + l.getLabel() + " at "
                + x.customer().getName() + " – " + reason + ". Bill " + p.txn().getBillNo() + ".");
        return result(p);
    }

    private SaleCalc damageCalc(Ctx x, CustomerDamageRequest r) {
        BottleInfo b = bottle(r.bottleTypeCode());
        return CustomerDamageRules.calculate(x.view(), b, n(r.qty()), r.mode() == null ? DamageMode.REPLACE : r.mode(),
                !Boolean.FALSE.equals(r.giveFilled()), r.reason(), price(x, b.code()), deposit(b.code(), x.date()));
    }

    // ================================================================== settle owed

    @Transactional(readOnly = true)
    public SalePreview previewSettleOwed(SettleOwedRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        return preview(x, settleCalc(x, r), common(x, r.paperBillNo(), false, r.paid()), r.paid());
    }

    @Transactional
    public SaleResult settleOwed(SettleOwedRequest r) {
        Ctx x = ctx(r.customerId(), r.date());
        SaleCalc calc = settleCalc(x, r);
        SettleMethod method = r.method() == null ? SettleMethod.RETURN : r.method();
        SaleLine l = calc.lines().getFirst();
        String details = "Bottles owed settled – " + x.customer().getName() + ": " + l.getOwedCleared() + " × " + l.getLabel()
                + (method == SettleMethod.DEPOSIT ? " by deposit " + Money.format(calc.amount()) : " returned");
        return post(new Spec(SalesKind.OWED_SETTLEMENT, x, null, r.paperBillNo(), false, calc, r.paid(), null, r.note(), false, null,
                method, r.approveOverLimit(), details, false));
    }

    private SaleCalc settleCalc(Ctx x, SettleOwedRequest r) {
        BottleInfo b = bottle(r.bottleTypeCode());
        return OwedSettlementRules.calculate(x.view(), b, n(r.qty()), r.method() == null ? SettleMethod.RETURN : r.method(),
                deposit(b.code(), x.date()));
    }

    // ================================================================== product sale

    @Transactional(readOnly = true)
    public SalePreview previewProductSale(ProductSaleRequest r) {
        Ctx x = r.customerId() == null ? walkIn(r.date()) : ctx(r.customerId(), r.date());
        return preview(x, productCalc(x, r), common(x, r.paperBillNo(), false, r.paid()), r.paid());
    }

    @Transactional
    public SaleResult productSale(ProductSaleRequest r) {
        Ctx x = r.customerId() == null ? walkIn(r.date()) : ctx(r.customerId(), r.date());
        SaleCalc calc = productCalc(x, r);
        String party = x.customer() == null ? Texts.blankToNull(r.walkInName()) : x.customer().getName();
        String details = "Product sale " + party + ": " + String.join(", ", calc.lines().stream()
                .map(l -> l.getQty() + " × " + l.getLabel()).toList()) + ", " + Money.format(calc.amount());
        PayMethod method = Money.isPositive(r.paid()) ? (r.payMethod() == null ? PayMethod.CASH : r.payMethod()) : null;
        return post(new Spec(SalesKind.PRODUCT_SALE, x, party, r.paperBillNo(), false, calc, r.paid(), method, r.note(), false, null,
                null, r.approveOverLimit(), details, false));
    }

    private SaleCalc productCalc(Ctx x, ProductSaleRequest r) {
        List<ProductSaleRules.Item> items = new ArrayList<>();
        if (r.items() != null) {
            for (ProductItemRequest i : r.items()) {
                if (i == null || Texts.blankToNull(i.productCode()) == null || n(i.qty()) <= 0) {
                    continue;
                }
                Product p = products.findByCode(i.productCode().trim())
                        .orElseThrow(() -> new ValidationException("items", "Unknown product " + i.productCode() + "."));
                items.add(new ProductSaleRules.Item(new ProductInfo(p.getCode(), p.getName(), p.getSellingPrice(), p.getCostPrice(),
                        p.isActive(), p.getStockQty()), n(i.qty()), i.unitPrice()));
            }
        }
        return ProductSaleRules.calculate(x.customer() == null ? null : x.view(), r.walkInName(), items, r.paid());
    }

    // ================================================================== reversal (BR-14)

    /**
     * Cancels a bill with a REVERSAL bill: the opposite ledger effects (may fail with BR-12 if stock has moved on),
     * negative amount and paid, links both ways, the account and the damage record updated, the paper bill number
     * free again. The next delivery date is not moved back.
     */
    @Transactional
    public SaleResult reverse(long id, ReverseRequest r) {
        SalesTxn t = txns.findById(id).orElseThrow(() -> new NotFoundException(SalesQueryService.ENTITY, id));
        if (!t.getKind().reversible()) {
            throw new BusinessException(NOT_REVERSIBLE, "This entry cannot be reversed.");
        }
        if (t.getReversedById() != null) {
            String by = txns.findById(t.getReversedById()).map(SalesTxn::getBillNo).orElse("?");
            throw new BusinessException(ALREADY_REVERSED, "Already reversed by " + by + ".");
        }
        String reason = Texts.blankToNull(r.reason());
        if (reason == null) {
            throw new ValidationException("reason", "Enter the reason for the reversal.");
        }
        LocalDate today = today();
        SalesTxn rev = txns.saveAndFlush(SalesTxn.reversalOf(t, numbering.next(SequenceNames.BILL), today,
                "Reversal of bill " + t.getBillNo() + ": " + reason));
        List<SalesTxnLine> original = txnLines.findByTxnIdOrderByLineNo(t.getId());
        Map<String, String> labels = queries.itemLabels();
        List<SalesTxnLine> copies = new ArrayList<>();
        for (SalesTxnLine l : original) {
            copies.add(SalesTxnLine.of(rev.getId(), l.getLineNo(), l.toSaleLine(labels.get(code(l))), -1));
        }
        txnLines.saveAll(copies);
        if (!ledger.effectsOf(SourceType.SALES_TXN, t.getId()).isEmpty()) {
            ledger.reverse(SourceType.SALES_TXN, t.getId(), new Posting(SourceType.SALES_TXN, rev.getId(), rev.getBillNo(), today,
                    "Reversal of " + t.getKind().label() + " " + t.getBillNo() + " – " + party(t), List.of()));
        }
        t.markReversedBy(rev.getId());
        txns.saveAndFlush(t);
        if (t.getCustomerId() != null) {
            accounts.add(t.getCustomerId(), rev.balanceChange());
        }
        damages.findBySourceTypeAndSourceId(SourceType.SALES_TXN.name(), t.getId()).forEach(d -> {
            d.markReversed();
            damages.save(d);
        });
        audit.log(AuditAction.REVERSE, t.getKind().label(), t.getBillNo(), "Reversal entry " + rev.getBillNo() + " – " + reason);
        return new SaleResult(queries.detail(rev.getId()), null);
    }

    // ================================================================== registration with bottles (FR-08 + FR-32)

    /** Bottles given on the registration date: filled per bottle type, old bottles handed in (one brand), cash paid. */
    public record GivenBottles(Map<String, Integer> qty, int oldBottles, Long oldBrandId, BigDecimal paid, boolean approveOverLimit) {

        public boolean any() {
            return qty != null && qty.values().stream().anyMatch(q -> q != null && q > 0);
        }
    }

    /**
     * Preview of the registration bills before the customer exists: prices for the customer type (or the agreed prices
     * given), deposits, old bottles, cash paid and the stock check.
     */
    @Transactional(readOnly = true)
    public RegistrationPreview previewRegistration(String name, long customerTypeId, PayType payType, BigDecimal creditLimit,
                                                   LocalDate date, Map<String, BigDecimal> agreed, GivenBottles given) {
        String typeName = customerTypes.findById(customerTypeId).map(t -> t.getName()).orElse("?");
        CustomerView view = new CustomerView(null, Texts.blankToNull(name) == null ? "The customer" : name.trim(), typeName, Map.of());
        List<Problem> problems = new ArrayList<>(registrationChecks(given, date));
        List<SaleLine> lines = new ArrayList<>();
        BigDecimal water = Money.ZERO;
        BigDecimal deposit = Money.ZERO;
        for (Planned p : plan(given)) {
            BigDecimal agreedPrice = agreed == null ? null : agreed.get(p.bottle().code());
            Price price = Money.isPositive(agreedPrice) ? new Price(Money.of(agreedPrice), true)
                    : pricing.standardPrice(customerTypeId, p.bottle().code(), date).map(v -> new Price(v, false)).orElse(null);
            SaleCalc calc = FirstPurchaseRules.calculate(view, p.bottle(), p.qty(), p.old(), p.brand(), price, deposit(p.bottle().code(), date));
            calc.problems().forEach(pr -> problems.add(new Problem(pr.field() == null ? null : "bottlesGiven", pr.code(), pr.message())));
            lines.addAll(calc.lines());
            water = water.add(calc.water());
            deposit = deposit.add(calc.deposit());
        }
        BigDecimal amount = Money.of(water.add(deposit));
        BigDecimal paid = Money.of(given.paid());
        if (paid.compareTo(amount) > 0) {
            problems.add(Problem.field("paid", "Cash paid cannot be more than the total (" + Money.format(amount) + ")."));
        }
        CreditCheck.Result credit = CreditCheck.check(payType, creditLimit, Money.ZERO, amount, paid);
        return new RegistrationPreview(lines, Money.of(water), Money.of(deposit), amount, paid,
                new CreditView(credit.balance(), credit.after(), credit.limit(), credit.exceeds()), problems);
    }

    /** Checks before the customer is saved: old bottles, cash paid, and filled stock (so nothing is saved when short). */
    @Transactional(readOnly = true)
    public void checkRegistration(GivenBottles given, LocalDate date) {
        List<Problem> problems = registrationChecks(given, date);
        if (!problems.isEmpty()) {
            throw toException(problems.getFirst());
        }
    }

    /**
     * One first purchase per bottle type given (largest bottle first), inside the registration's transaction. Cash
     * paid is spread over the bills in that order. The next delivery date chosen on registration is kept.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TxnDetail> registrationBills(long customerId, LocalDate date, GivenBottles given) {
        List<TxnDetail> out = new ArrayList<>();
        BigDecimal left = Money.of(given.paid());
        for (Planned p : plan(given)) {
            Ctx x = ctx(customerId, date);
            SaleCalc calc = FirstPurchaseRules.calculate(x.view(), p.bottle(), p.qty(), p.old(), p.brand(), price(x, p.bottle().code()),
                    deposit(p.bottle().code(), date));
            BigDecimal pay = left.min(calc.amount());
            left = left.subtract(pay);
            Posted posted = postAndSave(new Spec(SalesKind.FIRST_PURCHASE, x, null, null, false, calc, pay, null,
                    "Bottles given at registration", false, null, null, given.approveOverLimit(), firstDetails(x.customer(), calc), false));
            out.add(queries.detail(posted.txn().getId()));
        }
        if (left.signum() > 0) {
            throw new ValidationException("paid", "Cash paid cannot be more than the total of the bills.");
        }
        return out;
    }

    private record Planned(BottleInfo bottle, int qty, int old, BrandInfo brand) {
    }

    /** Bottle types given, largest first; old bottles go on the brand's bottle type (default: the first active brand given). */
    private List<Planned> plan(GivenBottles given) {
        List<Planned> out = new ArrayList<>();
        if (!given.any()) {
            return out;
        }
        List<BottleType> types = new ArrayList<>(bottleTypes.findAllById(given.qty().keySet()));
        if (types.size() != given.qty().keySet().size()) {
            throw new ValidationException("bottlesGiven", "Unknown bottle type.");
        }
        types.sort(Comparator.comparing(BottleType::getLitres).reversed());
        BrandInfo brand = given.oldBottles() > 0 ? registrationBrand(given) : null;
        for (BottleType t : types) {
            int q = n(given.qty().get(t.getCode()));
            if (q <= 0) {
                continue;
            }
            boolean oldHere = brand != null && brand.bottleTypeCode().equals(t.getCode());
            out.add(new Planned(info(t), q, oldHere ? Math.min(given.oldBottles(), q) : 0, oldHere ? brand : null));
        }
        return out;
    }

    private BrandInfo registrationBrand(GivenBottles given) {
        if (given.oldBrandId() != null) {
            return brand(given.oldBrandId());
        }
        return brands.findAllByOrderByActiveDescNameAsc().stream()
                .filter(b -> b.isActive() && n(given.qty().get(b.getBottleTypeCode())) > 0)
                .findFirst().map(this::brandInfo).orElse(null);
    }

    private List<Problem> registrationChecks(GivenBottles given, LocalDate date) {
        List<Problem> problems = new ArrayList<>();
        if (given.qty() != null) {
            for (Map.Entry<String, Integer> e : given.qty().entrySet()) {
                if (n(e.getValue()) < 0) {
                    problems.add(Problem.field("bottlesGiven", "Quantities cannot be negative."));
                }
            }
        }
        if (given.oldBottles() < 0) {
            problems.add(Problem.field("oldBottles", "Quantities cannot be negative."));
        }
        if (Money.of(given.paid()).signum() < 0) {
            problems.add(Problem.field("paid", "Paid amount cannot be negative."));
        }
        if (given.oldBottles() > 0) {
            BrandInfo brand = registrationBrand(given);
            if (brand == null || !brand.active()) {
                problems.add(Problem.field("oldBrandId", "Select an accepted old-bottle brand."));
            } else {
                int forType = n(given.qty() == null ? null : given.qty().get(brand.bottleTypeCode()));
                if (given.oldBottles() > forType) {
                    String label = bottleTypes.findById(brand.bottleTypeCode()).map(b -> LedgerRules.bottleLabel(b.getLitres())).orElse("");
                    problems.add(Problem.field("oldBottles", "Old bottles cannot be more than the " + label + " bottles given."));
                }
            }
        }
        if (given.any()) {
            for (Planned p : plan(given)) {
                int filled = ledger.balances(p.bottle().code()).getOrDefault(Bucket.FILLED, 0);
                if (p.qty() > filled) {
                    problems.add(new Problem("bottlesGiven", LedgerRules.STOCK_NEGATIVE, "Only " + filled + " filled " + p.bottle().label()
                            + " bottles in store."));
                }
            }
        }
        if (date.isAfter(today())) {
            problems.add(Problem.field("registeredOn", "Registration date cannot be in the future."));
        }
        return problems;
    }

    // ================================================================== opening balances (seed / data migration)

    /** An OPENING_BALANCE bill: the amount the customer owed in the old ledger (no stock change). */
    @Transactional(propagation = Propagation.MANDATORY)
    public SalesTxn openingBalance(long customerId, LocalDate date, BigDecimal amount, String note) {
        SalesTxn t = txns.saveAndFlush(new SalesTxn(numbering.next(SequenceNames.BILL), SalesKind.OPENING_BALANCE, date, customerId, null)
                .openingAmount(amount).details(null, note, false, null, null, null));
        accounts.add(customerId, t.balanceChange());
        audit.log(AuditAction.IMPORT, SalesKind.OPENING_BALANCE.label(), t.getBillNo(), Money.format(amount) + " – " + note);
        return t;
    }

    // ================================================================== the posting flow

    /** Who, what and how: everything {@link #post} needs. {@code walkInName} only for a walk-in product sale. */
    private record Spec(SalesKind kind, Ctx x, String walkInName, String paperBillNo, boolean paperRequired, SaleCalc calc,
                        BigDecimal paid, PayMethod payMethod, String payReference, String note, boolean phoneConfirmed,
                        DamageMode damageMode, SettleMethod settleMethod, Boolean approveOverLimit, String auditDetails,
                        boolean advanceSchedule) {

        /** A sale without a payment reference (only delivery bills take one). */
        Spec(SalesKind kind, Ctx x, String walkInName, String paperBillNo, boolean paperRequired, SaleCalc calc, BigDecimal paid,
             PayMethod payMethod, String note, boolean phoneConfirmed, DamageMode damageMode, SettleMethod settleMethod,
             Boolean approveOverLimit, String auditDetails, boolean advanceSchedule) {
            this(kind, x, walkInName, paperBillNo, paperRequired, calc, paid, payMethod, null, note, phoneConfirmed, damageMode,
                    settleMethod, approveOverLimit, auditDetails, advanceSchedule);
        }
    }

    private record Posted(SalesTxn txn, ScheduleChange schedule) {
    }

    private SaleResult post(Spec s) {
        return result(postAndSave(s));
    }

    private SaleResult result(Posted p) {
        return new SaleResult(queries.detail(p.txn().getId()), p.schedule());
    }

    private Posted postAndSave(Spec s) {
        Ctx x = s.x();
        Customer c = x.customer();
        List<Problem> problems = new ArrayList<>(common(x, s.paperBillNo(), s.paperRequired(), s.paid()));
        problems.addAll(s.calc().problems());
        if (!problems.isEmpty()) {
            throw toException(problems.getFirst());
        }
        SaleCalc calc = s.calc();
        BigDecimal paid = Money.of(s.paid());

        // Credit check on the locked balance (BR-09); the Admin may approve.
        String overrideBy = null;
        AuthUser approver = null;
        if (c != null) {
            CreditCheck.Result credit = CreditCheck.check(c.getPayType(), c.getCreditLimit(), accounts.lockedBalance(c.getId()),
                    calc.amount(), paid);
            if (credit.exceeds()) {
                if (!Boolean.TRUE.equals(s.approveOverLimit())) {
                    throw new BusinessException(CreditCheck.CREDIT_LIMIT, CreditCheck.message(c.getName(), credit), Map.of(
                            "limit", credit.limit(), "balance", credit.balance(), "after", credit.after()));
                }
                approver = CurrentUser.get().filter(u -> u.permissions().contains(Permissions.SALES_APPROVE_OVER_LIMIT))
                        .orElseThrow(() -> new AccessDeniedException(MSG_ONLY_ADMIN));
                overrideBy = approver.username();
            }
        }

        PayMethod method = s.payMethod() != null ? s.payMethod() : paid.signum() > 0 ? PayMethod.CASH : null;
        SalesTxn t = new SalesTxn(numbering.next(SequenceNames.BILL), s.kind(), x.date(), c == null ? null : c.getId(),
                c == null ? s.walkInName() : null)
                .amounts(calc.water(), calc.deposit(), calc.product(), calc.productCost(), paid, method)
                .details(Texts.blankToNull(s.paperBillNo()), Texts.blankToNull(s.note()), s.phoneConfirmed(), s.damageMode(),
                        s.settleMethod(), overrideBy)
                .payReference(s.payReference());
        t = txns.saveAndFlush(t);
        List<SalesTxnLine> lines = new ArrayList<>();
        for (int i = 0; i < calc.lines().size(); i++) {
            lines.add(SalesTxnLine.of(t.getId(), i + 1, calc.lines().get(i), 1));
        }
        txnLines.saveAll(lines);
        ledger.post(new Posting(SourceType.SALES_TXN, t.getId(), t.getBillNo(), x.date(), s.kind().label() + " – " + party(t, c),
                calc.effects()));
        if (c != null) {
            accounts.add(c.getId(), t.balanceChange());
        }
        audit.log(AuditAction.CREATE, s.kind().label(), t.getBillNo(), s.auditDetails());
        if (approver != null) {
            audit.log(AuditAction.APPROVE, "Credit limit", t.getBillNo(), "Over-limit sale approved by " + approver.fullName());
        }
        ScheduleChange schedule = null;
        if (c != null && s.advanceSchedule() && s.kind().advancesSchedule()) {
            schedule = advance(c, x.date());
        }
        return new Posted(t, schedule);
    }

    /** Design 5.4: the next delivery moves past the bill date in steps of the cycle (never back). */
    private ScheduleChange advance(Customer c, LocalDate billDate) {
        LocalDate before = jdbc.queryForObject("select next_delivery_date from customer where id = :c",
                new MapSqlParameterSource("c", c.getId()), LocalDate.class);
        LocalDate after = ScheduleRules.advance(before, c.getCycleWeeks(), billDate);
        if (!after.equals(before)) {
            jdbc.update("update customer set next_delivery_date = :d where id = :c",
                    new MapSqlParameterSource("d", after).addValue("c", c.getId()));
        }
        return new ScheduleChange(before, after);
    }

    /** Checks common to all sales: active customer, date, paid, paper bill number (BR-13). */
    private List<Problem> common(Ctx x, String paperBillNo, boolean paperRequired, BigDecimal paid) {
        List<Problem> out = new ArrayList<>();
        Customer c = x.customer();
        if (c != null && !c.isActive()) {
            out.add(new Problem(null, CUSTOMER_INACTIVE, c.getName() + " is inactive. Reactivate the customer before entering sales."));
        }
        LocalDate today = today();
        if (x.date().isAfter(today)) {
            out.add(Problem.field("date", "Date cannot be after today (" + BusinessDates.format(today) + ")."));
        }
        if (paid != null && paid.signum() < 0) {
            out.add(Problem.field("paid", "Paid amount cannot be negative."));
        }
        String paper = Texts.blankToNull(paperBillNo);
        if (paper == null) {
            if (paperRequired) {
                out.add(Problem.field("paperBillNo", "Enter the paper bill number."));
            }
        } else if (paper.length() > 30) {
            out.add(Problem.field("paperBillNo", "Paper bill number: at most 30 characters."));
        } else {
            txns.findActivePaperBill(paper).ifPresent(dup -> out.add(new Problem("paperBillNo", DUPLICATE_PAPER_BILL, "Paper bill " + paper
                    + " was already entered on " + BusinessDates.format(dup.getTxnDate()) + " (" + dup.getKind().label() + ", "
                    + party(dup) + "). A bill number can be entered only once.")));
        }
        return out;
    }

    /** First problem → 400 with the field, or 422 with its code (credit, duplicate, note required, rules). */
    private BusinessException toException(Problem p) {
        if (Problem.SALE_RULE.equals(p.code()) && p.field() != null) {
            return new ValidationException(p.field(), p.message());
        }
        if (DUPLICATE_PAPER_BILL.equals(p.code())) {
            return new BusinessException(p.code(), p.message(), Map.of("field", p.field()));
        }
        return new BusinessException(p.code(), p.message(), p.field() == null ? Map.of() : Map.of("field", p.field()));
    }

    private SalePreview preview(Ctx x, SaleCalc calc, List<Problem> common, BigDecimal paidIn) {
        BigDecimal paid = Money.of(paidIn);
        List<Problem> problems = new ArrayList<>(common);
        problems.addAll(calc.problems());
        List<BottlesAfter> after = new ArrayList<>();
        CreditView credit = null;
        if (x.customer() != null) {
            Map<String, String> labels = queries.itemLabels();
            calc.after().forEach((code, b) -> {
                Bottles now = x.view().bottles(code);
                boolean changed = !now.equals(b);
                if (changed || b.held() + b.toCollect() + b.owed() > 0) {
                    after.add(new BottlesAfter(code, labels.getOrDefault(code, code), b.held(), b.toCollect(), b.owed(), b.held() - now.held(),
                            b.toCollect() - now.toCollect(), b.owed() - now.owed()));
                }
            });
            CreditCheck.Result r = CreditCheck.check(x.customer().getPayType(), x.customer().getCreditLimit(),
                    accounts.balance(x.customer().getId()), calc.amount(), paid);
            credit = new CreditView(r.balance(), r.after(), r.limit(), r.exceeds());
        }
        return new SalePreview(calc.lines(), calc.water(), calc.deposit(), calc.product(), calc.amount(), paid, after, credit, problems,
                calc.warnings());
    }

    // ================================================================== inputs

    /** The customer (with bottles) and the bill date (default today). */
    private record Ctx(Customer customer, CustomerView view, LocalDate date) {
    }

    private Ctx ctx(Long customerId, LocalDate date) {
        if (customerId == null) {
            throw new ValidationException("customerId", "Select a customer.");
        }
        Customer c = customers.findById(customerId).orElseThrow(() -> new ValidationException("customerId", "Select a customer."));
        Map<String, Bottles> bottles = new LinkedHashMap<>();
        ledger.customerBalances(customerId).forEach((code, m) -> bottles.put(code,
                new Bottles(m.get(CustomerField.HELD), m.get(CustomerField.TO_COLLECT), m.get(CustomerField.OWED))));
        String typeName = customerTypes.findById(c.getCustomerTypeId()).map(t -> t.getName()).orElse("?");
        return new Ctx(c, new CustomerView(c.getId(), c.getName(), typeName, bottles), date == null ? today() : date);
    }

    private Ctx walkIn(LocalDate date) {
        return new Ctx(null, null, date == null ? today() : date);
    }

    private BottleInfo bottle(String code) {
        String c = code == null ? "" : code.trim();
        return bottleTypes.findById(c).map(SalesService::info)
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose the bottle type."));
    }

    private static BottleInfo info(BottleType t) {
        return new BottleInfo(t.getCode(), LedgerRules.bottleLabel(t.getLitres()), t.getName());
    }

    private Price price(Ctx x, String bottleTypeCode) {
        return pricing.waterPrice(x.customer().getId(), bottleTypeCode, x.date()).map(w -> new Price(w.price(), w.agreed())).orElse(null);
    }

    private BigDecimal deposit(String bottleTypeCode, LocalDate date) {
        return pricing.deposit(bottleTypeCode, date).orElse(null);
    }

    private BrandInfo brand(Long id) {
        return id == null ? null : brands.findById(id).map(this::brandInfo).orElse(null);
    }

    private BrandInfo brandInfo(OldBottleBrand b) {
        String bottleName = bottleTypes.findById(b.getBottleTypeCode()).map(BottleType::getName).orElse(b.getBottleTypeCode());
        return new BrandInfo(b.getId(), b.getName(), b.getBottleTypeCode(), bottleName, b.isActive());
    }

    private String party(SalesTxn t) {
        return party(t, t.getCustomerId() == null ? null : customers.findById(t.getCustomerId()).orElse(null));
    }

    private static String party(SalesTxn t, Customer c) {
        return c != null ? c.getName() : Optional.ofNullable(t.getWalkInName()).orElse("");
    }

    private static String code(SalesTxnLine l) {
        return l.getBottleTypeCode() != null ? l.getBottleTypeCode() : l.getProductCode();
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private LocalDate today() {
        return BusinessDates.today(clock);
    }
}
