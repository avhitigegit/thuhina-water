package lk.thuhina.water.audit.repository;

import java.util.List;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Insert-only repository: extends the bare {@link Repository} so no update / delete methods exist.
 * The filtered search for the Administration page is in {@code AuditQueryService} (Criteria query, read-only).
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog entry);

    Page<AuditLog> findAllByOrderByTsDescIdDesc(Pageable pageable);

    List<AuditLog> findByEntityAndRefOrderByIdAsc(String entity, String ref);

    List<AuditLog> findByUsernameAndActionOrderByIdAsc(String username, AuditAction action);
}
