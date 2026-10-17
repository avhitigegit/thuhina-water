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
