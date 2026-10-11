package lk.thuhina.water.masterdata.service;

import java.util.List;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.AreaRequest;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.AreaResponse;
import lk.thuhina.water.masterdata.model.Area;
import lk.thuhina.water.masterdata.repository.AreaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Delivery areas (= delivery routes): unique name, active. The screen for them comes with Customers (M05). */
@Service
public class AreaService {

    static final String ENTITY = "Area";
    static final String MSG_EXISTS = "Area already exists.";

    private final AreaRepository repository;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public AreaService(AreaRepository repository, AuditService audit, JdbcTemplate jdbc) {
        this.repository = repository;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<AreaResponse> list() {
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        jdbc.query("select area_id, count(*) from customer where status = 'ACTIVE' group by area_id", rs -> {
            counts.put(rs.getLong(1), rs.getLong(2));
        });
        return repository.findAllByOrderByNameAsc().stream().map(a -> toResponse(a, counts.getOrDefault(a.getId(), 0L))).toList();
    }

    @Transactional
    public AreaResponse create(AreaRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ValidationException("name", MSG_EXISTS);
        }
        Area a = new Area(name);
        if (request.active() != null && !request.active()) {
            a.update(name, false);
        }
        a = repository.saveAndFlush(a);
        audit.log(AuditAction.CREATE, ENTITY, a.getName(), a.isActive() ? "Active" : "Inactive");
        return toResponse(a);
    }

    @Transactional
    public AreaResponse update(long id, AreaRequest request) {
        Area a = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), a.getVersion(), ENTITY + " " + a.getName());
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ValidationException("name", MSG_EXISTS);
        }
        boolean active = request.active() == null ? a.isActive() : request.active();
        String before = a.getName() + ", " + (a.isActive() ? "Active" : "Inactive");
        String after = name + ", " + (active ? "Active" : "Inactive");
        if (before.equals(after)) {
            return toResponse(a);
        }
        a.update(name, active);
        repository.saveAndFlush(a);
        audit.log(AuditAction.UPDATE, ENTITY, a.getName(), before + " → " + after);
        return toResponse(a);
    }

    private static AreaResponse toResponse(Area a) {
        return toResponse(a, 0);
    }

    private static AreaResponse toResponse(Area a, long activeCustomers) {
        return new AreaResponse(a.getId(), a.getName(), a.isActive(), activeCustomers, a.getVersion());
    }
}
