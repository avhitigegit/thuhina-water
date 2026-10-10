package lk.thuhina.water.masterdata.service;

import java.time.Clock;
import java.util.List;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bottle types (FR-01): the code is made from the size; one bottle type per size; the size cannot change; inactive
 * types are hidden from sales and purchasing. Changes are audited before → after.
 * (The stock rows of a new bottle type are created in M04.)
 */
@Service
public class BottleTypeService {

    static final String ENTITY = "Bottle type";

    private final BottleTypeRepository repository;
    private final PricingService pricing;
    private final AuditService audit;
    private final Clock clock;

    public BottleTypeService(BottleTypeRepository repository, PricingService pricing, AuditService audit, Clock clock) {
        this.repository = repository;
        this.pricing = pricing;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BottleTypeResponse> list(boolean activeOnly) {
        List<BottleType> list = activeOnly ? repository.findByActiveTrueOrderByLitresDesc() : repository.findAllByOrderByLitresDesc();
        return list.stream().map(this::toResponse).toList();
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
        return toResponse(saved);
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
        return toResponse(b);
    }

    private static String describe(BottleType b) {
        return b.getName() + ", " + MasterDataRules.litresText(b.getLitres()) + "L, " + (b.isActive() ? "Active" : "Inactive");
    }

    private BottleTypeResponse toResponse(BottleType b) {
        return new BottleTypeResponse(b.getCode(), b.getName(), b.getLitres(), b.isActive(),
                pricing.deposit(b.getCode(), BusinessDates.today(clock)).orElse(null), null, b.getVersion());
    }
}
