package lk.thuhina.water.masterdata.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.BottleTypeResponse;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.CreateBottleTypeRequest;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.UpdateBottleTypeRequest;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.rules.MasterDataRules;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bottle types (FR-01): the code is made from the size; one bottle type per size; the size cannot change; inactive
 * types are hidden from sales and purchasing. Changes are audited before → after.
 * "In circulation" = the bottles of the type in every stock status except Written off.
 */
@Service
public class BottleTypeService {

    static final String ENTITY = "Bottle type";

    private final BottleTypeRepository repository;
    private final PricingService pricing;
    private final AuditService audit;
    private final Clock clock;
    private final NamedParameterJdbcTemplate jdbc;

    public BottleTypeService(BottleTypeRepository repository, PricingService pricing, AuditService audit, Clock clock,
            NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.pricing = pricing;
        this.audit = audit;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BottleTypeResponse> list(boolean activeOnly) {
        List<BottleType> list = activeOnly ? repository.findByActiveTrueOrderByLitresDesc() : repository.findAllByOrderByLitresDesc();
        Map<String, Integer> circulation = inCirculation();
        return list.stream().map(b -> toResponse(b, circulation.getOrDefault(b.getCode(), 0))).toList();
    }

    @Transactional
    public BottleTypeResponse create(CreateBottleTypeRequest request) {
        if (!MasterDataRules.isValidLitres(request.litres())) {
            throw new ValidationException("litres", "Size: more than 0 and at most 1000 litres, with up to 2 decimals.");
        }
        repository.findByLitres(request.litres()).ifPresent(existing -> {
            throw new ValidationException("litres", "A " + MasterDataRules.litresText(request.litres())
                    + "L bottle type already exists (" + existing.getName() + ").");
        });
        String code = MasterDataRules.bottleCode(request.litres());
        boolean active = request.active() == null || request.active();
        BottleType saved = repository.saveAndFlush(new BottleType(code, request.name().trim(), request.litres(), active));
        audit.log(AuditAction.CREATE, ENTITY, code, saved.getName());
        return toResponse(saved, inCirculation().getOrDefault(code, 0));
    }

    /** Where a bottle type counts as used (UAT finding 2): any of these rows means it must be set Inactive instead. */
    private static final String[][] USED_IN = {
            {"select count(*) from stock_movement where item_code = :c", "stock movements"},
            {"select count(*) from stock_balance where bottle_type_code = :c and qty <> 0", "stock"},
            {"select count(*) from sales_txn_line where bottle_type_code = :c", "bills"},
            {"select count(*) from ledger_effect where bottle_type_code = :c", "bills"},
            {"select count(*) from damage_record where bottle_type_code = :c", "damage records"},
            {"select count(*) from customer_usual_qty where bottle_type_code = :c", "customers"},
            {"select count(*) from customer_bottle_balance where bottle_type_code = :c and (held <> 0 or to_collect <> 0 or owed <> 0)", "customers"},
            {"select count(*) from customer_price where bottle_type_code = :c", "customer agreed prices"},
            {"select count(*) from factory_charge where bottle_type_code = :c", "filling factories"},
            {"select count(*) from factory_batch where bottle_type_code = :c", "factory batches"},
            {"select count(*) from cq_line where bottle_type_code = :c", "customer quotations"},
            {"select count(*) from supplier_item where item_type = 'BOTTLE' and item_code = :c", "suppliers"},
            {"select count(*) from qr_item where item_code = :c", "quotation requests"},
            {"select count(*) from quotation_line where item_code = :c", "supplier quotations"},
            {"select count(*) from po_line where item_code = :c", "purchase orders"},
            {"select count(*) from grn_line where item_code = :c", "goods received"},
            {"select count(*) from stock_adjustment where item_code = :c", "stock adjustments"},
    };

    /**
     * Delete a bottle type added by mistake and never used (UAT finding 2). Its setup rows (prices, deposit, minimum
     * stock, accepted old-bottle brands, empty stock rows) go with it. A used type is refused – set it Inactive.
     */
    @Transactional
    public void delete(String code) {
        BottleType b = repository.findById(code).orElseThrow(() -> new NotFoundException(ENTITY, code));
        Map<String, Object> p = Map.of("c", code);
        java.util.Set<String> usedBy = new java.util.LinkedHashSet<>();
        for (String[] q : USED_IN) {
            Integer n = jdbc.queryForObject(q[0], p, Integer.class);
            if (n != null && n > 0) {
                usedBy.add(q[1]);
            }
        }
        if (!usedBy.isEmpty()) {
            throw new ValidationException("code", b.getName() + " is already used (" + String.join(", ", usedBy)
                    + ") and cannot be deleted. Set it Inactive instead.");
        }
        jdbc.update("delete from old_bottle_brand where bottle_type_code = :c", p);
        jdbc.update("delete from min_stock_level where bottle_type_code = :c", p);
        jdbc.update("delete from stock_balance where bottle_type_code = :c", p);
        jdbc.update("delete from customer_bottle_balance where bottle_type_code = :c", p);
        // price_entry is insert-only; V020 allows this transaction to remove the type's own prices.
        jdbc.getJdbcTemplate().queryForObject("select set_config('app.price_cancel', 'on', true)", String.class);
        jdbc.update("delete from price_entry where bottle_type_code = :c", p);
        jdbc.update("delete from bottle_type where code = :c", p);
        audit.log(AuditAction.DELETE, ENTITY, code, b.getName() + " deleted (never used)");
    }

    @Transactional
    public BottleTypeResponse update(String code, UpdateBottleTypeRequest request) {
        BottleType b = repository.findById(code).orElseThrow(() -> new NotFoundException(ENTITY, code));
        Versions.check(request.version(), b.getVersion(), ENTITY + " " + code);
        String before = describe(b);
        b.update(request.name().trim(), request.active());
        String after = describe(b);
        if (!before.equals(after)) {
            repository.saveAndFlush(b);
            audit.log(AuditAction.UPDATE, ENTITY, code, before + " → " + after);
        }
        return toResponse(b, inCirculation().getOrDefault(code, 0));
    }

    private static String describe(BottleType b) {
        return b.getName() + ", " + MasterDataRules.litresText(b.getLitres()) + "L, " + (b.isActive() ? "Active" : "Inactive");
    }

    private Map<String, Integer> inCirculation() {
        Map<String, Integer> out = new HashMap<>();
        jdbc.query("select bottle_type_code, sum(qty) from stock_balance where bucket <> 'WRITTEN_OFF' group by bottle_type_code",
                rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                });
        return out;
    }

    private BottleTypeResponse toResponse(BottleType b, int inCirculation) {
        return new BottleTypeResponse(b.getCode(), b.getName(), b.getLitres(), b.isActive(),
                pricing.deposit(b.getCode(), BusinessDates.today(clock)).orElse(null), inCirculation, b.getVersion());
    }
}
