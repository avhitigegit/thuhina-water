package lk.thuhina.water.masterdata.service;

import java.util.List;
import java.util.Objects;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CreateCustomerTypeRequest;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CustomerTypeResponse;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.UpdateCustomerTypeRequest;
import lk.thuhina.water.masterdata.model.CustomerType;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer types (FR-03): unique name, description, active. The name cannot be changed; a type cannot be deactivated
 * while active customers use it (checked with SQL so this package does not depend on customers).
 */
@Service
public class CustomerTypeService {

    static final String ENTITY = "Customer type";
    public static final String TYPE_IN_USE = "TYPE_IN_USE";

    private final CustomerTypeRepository repository;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public CustomerTypeService(CustomerTypeRepository repository, AuditService audit, JdbcTemplate jdbc) {
        this.repository = repository;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /** Active customers of each type – shown in the Customer types pop-up. */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> activeCustomerCounts() {
        java.util.Map<Long, Long> out = new java.util.HashMap<>();
        jdbc.query("select customer_type_id, count(*) from customer where status = 'ACTIVE' group by customer_type_id",
                rs -> {
                    out.put(rs.getLong(1), rs.getLong(2));
                });
        return out;
    }

    @Transactional(readOnly = true)
    public List<CustomerTypeResponse> list() {
        java.util.Map<Long, Long> counts = activeCustomerCounts();
        return repository.findAllByOrderByIdAsc().stream().map(t -> toResponse(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    @Transactional
    public CustomerTypeResponse create(CreateCustomerTypeRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ValidationException("name", "Customer type already exists.");
        }
        CustomerType t = repository.saveAndFlush(new CustomerType(name, Texts.blankToNull(request.description())));
        audit.log(AuditAction.CREATE, ENTITY, t.getName(), t.getDescription() == null ? "" : t.getDescription());
        return toResponse(t, 0);
    }

    @Transactional
    public CustomerTypeResponse update(long id, UpdateCustomerTypeRequest request) {
        CustomerType t = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), t.getVersion(), ENTITY + " " + t.getName());
        String description = Texts.blankToNull(request.description());
        if (Objects.equals(description, t.getDescription()) && request.active() == t.isActive()) {
            return toResponse(t, activeCustomerCounts().getOrDefault(t.getId(), 0L));
        }
        if (t.isActive() && !request.active()) {
            Long inUse = jdbc.queryForObject("select count(*) from customer where customer_type_id = ? and status = 'ACTIVE'",
                    Long.class, t.getId());
            if (inUse != null && inUse > 0) {
                throw new BusinessException(TYPE_IN_USE, "Active customers use this type. Move them first.",
                        java.util.Map.of("customers", inUse));
            }
        }
        t.update(description, request.active());
        repository.saveAndFlush(t);
        audit.log(AuditAction.UPDATE, ENTITY, t.getName(),
                (t.isActive() ? "Active" : "Inactive") + " – " + (description == null ? "" : description));
        return toResponse(t, activeCustomerCounts().getOrDefault(t.getId(), 0L));
    }

    private static CustomerTypeResponse toResponse(CustomerType t, long activeCustomers) {
        return new CustomerTypeResponse(t.getId(), t.getName(), t.getDescription(), t.isActive(), activeCustomers, t.getVersion());
    }
}
