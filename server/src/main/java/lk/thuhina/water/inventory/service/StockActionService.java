package lk.thuhina.water.inventory.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.inventory.dto.StockDtos.AdjustmentRequest;
import lk.thuhina.water.inventory.dto.StockDtos.AdjustmentResponse;
import lk.thuhina.water.inventory.dto.StockDtos.CompanyDamageRequest;
import lk.thuhina.water.inventory.dto.StockDtos.DamageListRow;
import lk.thuhina.water.inventory.model.DamageRecord;
import lk.thuhina.water.inventory.model.MinStockLevel;
import lk.thuhina.water.inventory.model.StockAdjustment;
import lk.thuhina.water.inventory.repository.DamageRecordRepository;
import lk.thuhina.water.inventory.repository.MinStockLevelRepository;
import lk.thuhina.water.inventory.repository.StockAdjustmentRepository;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock page actions (M04): company damage (FR-29, BR-05), stock adjustments (FR-30) and minimum levels (FR-31).
 * Every stock change goes through the Ledger Engine, so BR-12 (never below zero) applies and Movements shows it.
 */
@Service
public class StockActionService {

    public static final String NO_CHANGE = "NO_CHANGE";

    private final LedgerService ledger;
    private final DamageRecordRepository damages;
    private final StockAdjustmentRepository adjustments;
    private final MinStockLevelRepository minLevels;
    private final BottleTypeRepository bottleTypes;
    private final ProductRepository products;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public StockActionService(LedgerService ledger, DamageRecordRepository damages, StockAdjustmentRepository adjustments,
                              MinStockLevelRepository minLevels, BottleTypeRepository bottleTypes, ProductRepository products,
                              NumberingService numbering, AuditService audit, Clock clock) {
        this.ledger = ledger;
        this.damages = damages;
        this.adjustments = adjustments;
        this.minLevels = minLevels;
        this.bottleTypes = bottleTypes;
        this.products = products;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    /** Company damage: the bottles leave their place and are written off at company cost. */
    @Transactional
    public DamageListRow companyDamage(CompanyDamageRequest r) {
        checkDate(r.date());
        BottleType bottle = bottleTypes.findById(r.bottleTypeCode().trim())
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose the bottle type."));
        if (r.qty() < 1) {
            throw new ValidationException("qty", "Enter the number of damaged bottles.");
        }
        String reason = r.reason().trim();
        DamageRecord d = damages.saveAndFlush(DamageRecord.company(numbering.next(SequenceNames.DAMAGE), r.date(),
                bottle.getCode(), r.qty(), r.location(), reason, Texts.blankToNull(r.note())));
        ledger.post(new Posting(SourceType.DAMAGE, d.getId(), d.getDamageNo(), r.date(),
                "Company damage – written off (" + r.location().label() + ")",
                List.of(Effect.stock(bottle.getCode(), r.location().bucket(), -r.qty()),
                        Effect.stock(bottle.getCode(), Bucket.WRITTEN_OFF, r.qty()))));
        String label = LedgerRules.bottleLabel(bottle.getLitres());
        audit.log(AuditAction.CREATE, "Damage (company)", d.getDamageNo(), r.qty() + " × " + label + " "
                + r.location().label() + " – " + reason + ". Written off at company cost.");
        return new DamageListRow(d.getDamageNo(), d.getDamageDate(), "DAMAGED", bottle.getCode(), bottle.getName(), d.getQty(),
                "COMPANY", r.location().label(), null, reason, d.getCreatedBy(), null, false);
    }

    /**
     * Physical count (bucket set to the counted quantity), lost bottles (written off), or a product count.
     * The system quantity is read with a lock, so the counted difference is exact even while others post.
     */
    @Transactional
    public AdjustmentResponse adjust(AdjustmentRequest r) {
        checkDate(r.date());
        String reason = r.reason().trim();
        String code = r.itemCode().trim();
        BottleType bottle = bottleTypes.findById(code).orElse(null);
        Product product = bottle == null ? products.findByCode(code).orElse(null) : null;
        if (bottle == null && product == null) {
            throw new ValidationException("itemCode", "Choose the item.");
        }

        String bucketName;
        int system;
        int delta;
        Integer counted = null;
        String description;
        List<Effect> effects;
        if (product != null) {
            if (r.mode() != StockAdjustment.Mode.COUNT) {
                throw new ValidationException("mode", "Products are adjusted by a physical count.");
            }
            counted = requireCounted(r.counted());
            system = ledger.lockedProductStock(code);
            delta = counted - system;
            bucketName = "STOCK";
            description = product.getName() + ": system " + system + " → counted " + counted;
            effects = List.of(Effect.product(code, delta));
        } else {
            Bucket bucket = adjustableBucket(r.bucket());
            String label = LedgerRules.bottleLabel(bottle.getLitres());
            system = ledger.lockedStock(code, bucket);
            bucketName = bucket.name();
            if (r.mode() == StockAdjustment.Mode.LOST) {
                if (r.qty() == null || r.qty() < 1) {
                    throw new ValidationException("qty", "Enter the number of bottles lost.");
                }
                delta = -r.qty();
                description = r.qty() + " × " + label + " lost from " + bucket.label() + " – written off";
                effects = List.of(Effect.stock(code, bucket, -r.qty()), Effect.stock(code, Bucket.WRITTEN_OFF, r.qty()));
            } else {
                counted = requireCounted(r.counted());
                delta = counted - system;
                description = label + " " + bucket.label() + ": system " + system + " → counted " + counted;
                effects = List.of(Effect.stock(code, bucket, delta));
            }
        }
        if (delta == 0) {
            throw new BusinessException(NO_CHANGE, "Counted quantity matches the system. No adjustment needed.");
        }

        StockAdjustment a = adjustments.save(new StockAdjustment(numbering.next(SequenceNames.STOCK_ADJUSTMENT), r.date(), code,
                bucketName, r.mode(), system, counted, delta, reason));
        ledger.post(new Posting(SourceType.ADJUSTMENT, a.getId(), a.getAdjNo(), r.date(), "Stock adjustment – " + reason, effects));
        audit.log(AuditAction.CREATE, "Stock adjustment", a.getAdjNo(), description + " – " + reason);
        return new AdjustmentResponse(a.getAdjNo(), code, bucketName, r.mode().name(), system, counted, delta, description);
    }

    /** Minimum filled level (FR-31); audited "Filled 20L: 150 → 160". */
    @Transactional
    public int setMinLevel(String bottleTypeCode, Integer minFilled) {
        BottleType bottle = bottle(bottleTypeCode);
        if (minFilled == null || minFilled < 0) {
            throw new ValidationException("minFilled", "Minimum level cannot be negative.");
        }
        MinStockLevel level = minLevels.findById(bottle.getCode()).orElse(null);
        Integer before = level == null ? null : level.getMinFilled();
        if (Objects.equals(before, minFilled)) {
            return minFilled;
        }
        if (level == null) {
            level = new MinStockLevel(bottle.getCode());
        }
        level.set(minFilled, Instant.now(clock), CurrentUser.usernameOrSystem());
        minLevels.save(level);
        audit.log(AuditAction.UPDATE, "Minimum stock level", bottle.getCode(), "Filled " + LedgerRules.bottleLabel(bottle.getLitres())
                + ": " + (before == null ? "–" : before) + " → " + minFilled);
        return minFilled;
    }

    // ------------------------------------------------------------------ helpers

    private void checkDate(LocalDate date) {
        LocalDate today = BusinessDates.today(clock);
        if (date.isAfter(today)) {
            throw new ValidationException("date", "Date cannot be after today (" + BusinessDates.format(today) + ").");
        }
    }

    private BottleType bottle(String code) {
        return bottleTypes.findById(code == null ? "" : code.trim())
                .orElseThrow(() -> new NotFoundException("Bottle type", code));
    }

    private static int requireCounted(Integer counted) {
        if (counted == null || counted < 0) {
            throw new ValidationException("counted", "Enter the counted quantity.");
        }
        return counted;
    }

    /** Bottles can be counted or lost in store or at the factory – not "with customers" or "written off". */
    private static Bucket adjustableBucket(String bucket) {
        if (bucket == null) {
            throw new ValidationException("bucket", "Select the stock status to adjust.");
        }
        try {
            Bucket b = Bucket.valueOf(bucket);
            if (b == Bucket.EMPTY || b == Bucket.FILLED || b == Bucket.FACTORY) {
                return b;
            }
        } catch (IllegalArgumentException ignored) {
            // falls through to the message below
        }
        throw new ValidationException("bucket", "Select the stock status to adjust.");
    }
}
