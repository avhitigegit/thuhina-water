package lk.thuhina.water.masterdata.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.CreateOldBottleBrandRequest;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.OldBottleBrandResponse;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.UpdateOldBottleBrandRequest;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.OldBottleBrand;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.OldBottleBrandRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepted old-bottle brands (FR-06, BR-02): a brand handed in replaces the deposit for one bottle of that type.
 * "Stop accepting" / "Accept again" switch it off and on; it is never deleted.
 */
@Service
public class OldBottleBrandService {

    static final String ENTITY = "Accepted old-bottle brand";

    private final OldBottleBrandRepository repository;
    private final BottleTypeRepository bottleTypes;
    private final AuditService audit;
    private final Clock clock;

    public OldBottleBrandService(OldBottleBrandRepository repository, BottleTypeRepository bottleTypes, AuditService audit,
                                 Clock clock) {
        this.repository = repository;
        this.bottleTypes = bottleTypes;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OldBottleBrandResponse> list() {
        Map<String, String> names = bottleTypes.findAll().stream()
                .collect(Collectors.toMap(BottleType::getCode, BottleType::getName));
        return repository.findAllByOrderByActiveDescNameAsc().stream().map(b -> toResponse(b, names.get(b.getBottleTypeCode()))).toList();
    }

    @Transactional
    public OldBottleBrandResponse create(CreateOldBottleBrandRequest request) {
        BottleType bottle = activeBottle(request.bottleTypeCode());
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndBottleTypeCode(name, bottle.getCode())) {
            throw new ValidationException("name", name + " is already in the list for " + bottle.getName() + ".");
        }
        OldBottleBrand b = repository.saveAndFlush(new OldBottleBrand(name, bottle.getCode(),
                Texts.blankToNull(request.note()), BusinessDates.today(clock)));
        audit.log(AuditAction.CREATE, ENTITY, b.getName(), "For " + bottle.getName());
        return toResponse(b, bottle.getName());
    }

    @Transactional
    public OldBottleBrandResponse update(long id, UpdateOldBottleBrandRequest request) {
        OldBottleBrand b = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), b.getVersion(), ENTITY + " " + b.getName());
        BottleType bottle = request.bottleTypeCode().equals(b.getBottleTypeCode())
                ? bottleTypes.findById(b.getBottleTypeCode()).orElseThrow()
                : activeBottle(request.bottleTypeCode());
        String note = Texts.blankToNull(request.note());
        boolean changed = request.active() != b.isActive() || !Objects.equals(note, b.getNote())
                || !bottle.getCode().equals(b.getBottleTypeCode());
        if (changed) {
            String details = request.active() != b.isActive()
                    ? (request.active() ? "Accepted again" : "Stopped accepting")
                    : "For " + bottle.getName() + (note == null ? "" : " – " + note);
            b.update(bottle.getCode(), note, request.active());
            repository.saveAndFlush(b);
            audit.log(AuditAction.UPDATE, ENTITY, b.getName(), details);
        }
        return toResponse(b, bottle.getName());
    }

    private BottleType activeBottle(String code) {
        return bottleTypes.findById(code).filter(BottleType::isActive)
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose an active bottle type."));
    }

    private static OldBottleBrandResponse toResponse(OldBottleBrand b, String bottleName) {
        return new OldBottleBrandResponse(b.getId(), b.getName(), b.getBottleTypeCode(), bottleName, b.getNote(), b.isActive(),
                b.getAddedOn(), null, b.getVersion());
    }
}
