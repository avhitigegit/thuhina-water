package lk.thuhina.water.masterdata.service;

import java.util.List;
import java.util.Objects;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CreateCustomerTypeRequest;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CustomerTypeResponse;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.UpdateCustomerTypeRequest;
import lk.thuhina.water.masterdata.model.CustomerType;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer types (FR-03): unique name, description, active. The name cannot be changed. (M05 adds: a type cannot be
 * deactivated while active customers use it.)
 */
@Service
public class CustomerTypeService {

    static final String ENTITY = "Customer type";

    private final CustomerTypeRepository repository;
    private final AuditService audit;

    public CustomerTypeService(CustomerTypeRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CustomerTypeResponse> list() {
        return repository.findAllByOrderByIdAsc().stream().map(CustomerTypeService::toResponse).toList();
    }

    @Transactional
    public CustomerTypeResponse create(CreateCustomerTypeRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameIgnoreCase(name)) {
            throw new ValidationException("name", "Customer type already exists.");
        }
        CustomerType t = repository.saveAndFlush(new CustomerType(name, Texts.blankToNull(request.description())));
        audit.log(AuditAction.CREATE, ENTITY, t.getName(), t.getDescription() == null ? "" : t.getDescription());
        return toResponse(t);
    }

    @Transactional
    public CustomerTypeResponse update(long id, UpdateCustomerTypeRequest request) {
        CustomerType t = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), t.getVersion(), ENTITY + " " + t.getName());
        String description = Texts.blankToNull(request.description());
        if (Objects.equals(description, t.getDescription()) && request.active() == t.isActive()) {
            return toResponse(t);
        }
        t.update(description, request.active());
        repository.saveAndFlush(t);
        audit.log(AuditAction.UPDATE, ENTITY, t.getName(),
                (t.isActive() ? "Active" : "Inactive") + " – " + (description == null ? "" : description));
        return toResponse(t);
    }

    private static CustomerTypeResponse toResponse(CustomerType t) {
        return new CustomerTypeResponse(t.getId(), t.getName(), t.getDescription(), t.isActive(), t.getVersion());
    }
}
