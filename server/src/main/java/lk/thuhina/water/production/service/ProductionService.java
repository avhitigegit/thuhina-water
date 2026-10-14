package lk.thuhina.water.production.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.inventory.model.DamageRecord;
import lk.thuhina.water.inventory.repository.DamageRecordRepository;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.model.Factory;
import lk.thuhina.water.partners.repository.FactoryRepository;
import lk.thuhina.water.partners.service.FactoryService;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchLine;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchRequest;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchView;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryPaymentRequest;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryPaymentRow;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnRequest;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnResult;
import lk.thuhina.water.production.dto.ProductionDtos.BatchView;
import lk.thuhina.water.production.model.FactoryBatch;
import lk.thuhina.water.production.model.FactoryDispatch;
import lk.thuhina.water.production.model.FactoryPayment;
import lk.thuhina.water.production.model.FactoryReturn;
import lk.thuhina.water.production.repository.FactoryBatchRepository;
import lk.thuhina.water.production.repository.FactoryDispatchRepository;
import lk.thuhina.water.production.repository.FactoryPaymentRepository;
import lk.thuhina.water.production.repository.FactoryReturnRepository;
import lk.thuhina.water.production.rules.ProductionRules;
import lk.thuhina.water.sales.model.PayMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filling Factory actions (FR-23, FR-24, FR-26, BR-11, BR-12):
 * <ul>
 *   <li>send empties – one dispatch for every bottle type, one batch each, all-or-nothing; the charge on the dispatch date
 *       is copied to the batch; EMPTY −Q, FACTORY +Q through the Ledger Engine (never below zero);</li>
 *   <li>receive filled – partial returns; FACTORY −(filled + rejected), FILLED +filled, WRITTEN_OFF +rejected; rejects become a
 *       company damage record "At factory"; cost = filled × batch charge;</li>
 *   <li>pay a factory for a month.</li>
 * </ul>
 */
@Service
public class ProductionService {

    public static final String NO_CHARGE = "NO_CHARGE";

    private final FactoryDispatchRepository dispatches;
    private final FactoryBatchRepository batches;
    private final FactoryReturnRepository returns;
    private final FactoryPaymentRepository payments;
    private final DamageRecordRepository damages;
    private final FactoryRepository factories;
    private final FactoryService factoryService;
    private final BottleTypeRepository bottleTypes;
    private final ProductionQueryService query;
    private final LedgerService ledger;
    private final NumberingService numbering;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public ProductionService(FactoryDispatchRepository dispatches, FactoryBatchRepository batches, FactoryReturnRepository returns,
                             FactoryPaymentRepository payments, DamageRecordRepository damages, FactoryRepository factories,
                             FactoryService factoryService, BottleTypeRepository bottleTypes, ProductionQueryService query,
                             LedgerService ledger, NumberingService numbering, AuditService audit, NamedParameterJdbcTemplate jdbc,
                             Clock clock) {
        this.dispatches = dispatches;
        this.batches = batches;
        this.returns = returns;
        this.payments = payments;
        this.damages = damages;
        this.factories = factories;
        this.factoryService = factoryService;
        this.bottleTypes = bottleTypes;
        this.query = query;
        this.ledger = ledger;
        this.numbering = numbering;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ send empties (FR-23)

    @Transactional
    public DispatchView dispatch(DispatchRequest r) {
        checkNotFuture(r.date());
        Factory factory = r.factoryId() == null ? null : factories.findById(r.factoryId()).orElse(null);
        if (factory == null || !factory.isActive()) {
            throw new ValidationException("factoryId", "Select an active filling factory.");
        }
        Map<String, BottleType> all = bottleTypes.findAllByOrderByLitresDesc().stream()
                .collect(Collectors.toMap(BottleType::getCode, b -> b, (a, b) -> a, LinkedHashMap::new));
        Map<String, Integer> qty = new LinkedHashMap<>();
        for (DispatchLine l : r.lines() == null ? List.<DispatchLine>of() : r.lines()) {
            if (l == null || l.bottleTypeCode() == null || l.bottleTypeCode().isBlank()) {
                continue;
            }
            int q = l.qty() == null ? 0 : l.qty();
            if (q < 0) {
                throw new ValidationException("lines", "Quantity cannot be negative.");
            }
            if (q == 0) {
                continue;
            }
            BottleType b = all.get(l.bottleTypeCode().trim());
            if (b == null) {
                throw new ValidationException("lines", "Unknown bottle type " + l.bottleTypeCode() + ".");
            }
            if (!b.isActive()) {
                throw new ValidationException("lines", b.getName() + " is not active.");
            }
            if (qty.putIfAbsent(b.getCode(), q) != null) {
                throw new ValidationException("lines", b.getName() + " is listed twice.");
            }
        }
        if (qty.isEmpty()) {
            throw new ValidationException("lines", "Enter the number of empty bottles sent for at least one bottle type.");
        }
        // Largest bottle first, so batch numbers follow the order of the screen.
        List<BottleType> sent = qty.keySet().stream().map(all::get)
                .sorted(Comparator.comparing(BottleType::getLitres).reversed()).toList();
        Map<String, BigDecimal> charges = new LinkedHashMap<>();
        for (BottleType b : sent) {
            BigDecimal charge = factoryService.currentCharge(factory.getId(), b.getCode(), r.date())
                    .orElseThrow(() -> new BusinessException(NO_CHARGE, factory.getName() + " has no filling charge for " + b.getName()
                            + ". Set it under Suppliers & Factories.", Map.of("bottleTypeCode", b.getCode())));
            charges.put(b.getCode(), charge);
        }

        FactoryDispatch d = dispatches.saveAndFlush(new FactoryDispatch(numbering.next(SequenceNames.FACTORY_DISPATCH), factory.getId(),
                r.date(), Texts.blankToNull(r.vehicleNo()), Texts.blankToNull(r.note())));
        List<Effect> effects = new ArrayList<>();
        List<String> what = new ArrayList<>();
        List<String> batchNos = new ArrayList<>();
        for (BottleType b : sent) {
            int q = qty.get(b.getCode());
            FactoryBatch batch = batches.save(new FactoryBatch(numbering.next(SequenceNames.FACTORY_BATCH), d, b.getCode(), q,
                    charges.get(b.getCode())));
            batchNos.add(batch.getBatchNo());
            effects.add(Effect.stock(b.getCode(), Bucket.EMPTY, -q));
            effects.add(Effect.stock(b.getCode(), Bucket.FACTORY, q));
            what.add(q + " × " + LedgerRules.bottleLabel(b.getLitres()));
        }
        batches.flush();
        ledger.post(new Posting(SourceType.BATCH, d.getId(), d.getDispatchNo(), r.date(), "Dispatched to " + factory.getName(), effects));
        audit.log(AuditAction.CREATE, "Factory dispatch", d.getDispatchNo(), String.join(" + ", what) + " empties sent to "
                + factory.getName() + " (" + String.join(", ", batchNos) + ")");
        return query.dispatch(d.getDispatchNo());
    }

    // ------------------------------------------------------------------ receive filled (FR-24, FR-26)

    @Transactional
    public ReturnResult receive(long batchId, ReturnRequest r) {
        FactoryBatch b = batches.lockById(batchId).orElseThrow(() -> new NotFoundException("Batch", batchId));
        checkNotFuture(r.date());
        if (r.date().isBefore(b.getDispatchDate())) {
            throw new ValidationException("date", "Return date cannot be before the dispatch date.");
        }
        int[] back = jdbc.queryForObject("select coalesce(sum(filled_qty), 0), coalesce(sum(rejected_qty), 0) from factory_return "
                + "where batch_id = :id", new MapSqlParameterSource("id", batchId), (rs, i) -> new int[] {rs.getInt(1), rs.getInt(2)});
        int left = ProductionRules.stillAtFactory(b.getQtySent(), back[0], back[1]);
        int filled = r.filled() == null ? 0 : r.filled();
        int rejected = r.rejected() == null ? 0 : r.rejected();
        String reason = Texts.blankToNull(r.rejectReason());
        String problem = ProductionRules.returnProblem(left, filled, rejected, reason);
        if (problem != null) {
            throw new ValidationException(problem.startsWith("Enter the reason") ? "rejectReason" : "filled", problem);
        }

        BigDecimal cost = ProductionRules.cost(filled, b.getChargePerBottle());
        FactoryReturn ret = returns.saveAndFlush(new FactoryReturn(numbering.next(SequenceNames.FACTORY_RETURN), b.getId(), r.date(),
                filled, rejected, reason, cost, Texts.blankToNull(r.note())));
        String damageNo = null;
        if (rejected > 0) {
            damageNo = damages.save(DamageRecord.factoryReject(numbering.next(SequenceNames.DAMAGE), r.date(), b.getBottleTypeCode(),
                    rejected, reason, "Rejected in batch " + b.getBatchNo(), ret.getId())).getDamageNo();
        }
        ledger.post(new Posting(SourceType.RETURN, ret.getId(), ret.getReturnNo(), r.date(),
                "Returned from factory – " + b.getBatchNo() + (rejected > 0 ? " (" + rejected + " rejected)" : ""),
                List.of(Effect.stock(b.getBottleTypeCode(), Bucket.FACTORY, -(filled + rejected)),
                        Effect.stock(b.getBottleTypeCode(), Bucket.FILLED, filled),
                        Effect.stock(b.getBottleTypeCode(), Bucket.WRITTEN_OFF, rejected))));
        audit.log(AuditAction.CREATE, "Factory return", ret.getReturnNo(), b.getBatchNo() + ": " + filled + " filled, " + rejected
                + " rejected" + (reason == null ? "" : " (" + reason + ")") + "; filling cost " + Money.format(cost) + " (" + filled + " × "
                + Money.format(b.getChargePerBottle()) + ")");
        BatchView after = query.batch(b.getId());
        return new ReturnResult(after.returns().stream().filter(x -> x.id() == ret.getId()).findFirst().orElseThrow(), after, damageNo);
    }

    // ------------------------------------------------------------------ factory payments (FR-26)

    @Transactional
    public FactoryPaymentRow pay(FactoryPaymentRequest r) {
        Factory factory = r.factoryId() == null ? null : factories.findById(r.factoryId()).orElse(null);
        if (factory == null) {
            throw new ValidationException("factoryId", "Select the factory.");
        }
        if (!ProductionRules.isPeriod(r.period())) {
            throw new ValidationException("period", "Select the month.");
        }
        checkNotFuture(r.date());
        if (r.amount() == null || Money.of(r.amount()).signum() <= 0) {
            throw new ValidationException("amount", "Enter the amount paid.");
        }
        if (r.method() == null) {
            throw new ValidationException("method", "Select the payment method.");
        }
        String reference = Texts.blankToNull(r.reference());
        if (r.method() != PayMethod.CASH && reference == null) {
            throw new ValidationException("reference", "Enter the cheque number or bank reference.");
        }
        BigDecimal amount = Money.of(r.amount());
        FactoryPayment p = payments.saveAndFlush(new FactoryPayment(numbering.next(SequenceNames.FACTORY_PAYMENT), factory.getId(),
                r.period(), r.date(), amount, r.method(), reference, Texts.blankToNull(r.note())));
        audit.log(AuditAction.CREATE, "Factory payment", p.getFpNo(), factory.getName() + ": " + Money.format(amount) + " by "
                + r.method().label() + (reference == null ? "" : " (" + reference + ")") + " for " + ProductionRules.periodLabel(r.period()));
        return query.payment(p.getId());
    }

    private void checkNotFuture(LocalDate date) {
        LocalDate today = BusinessDates.today(clock);
        if (date.isAfter(today)) {
            throw new ValidationException("date", "Date cannot be after today (" + BusinessDates.format(today) + ").");
        }
    }
}
