package lk.thuhina.water.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class AuditServiceIntegrationTest extends IntegrationTest {

    @Autowired
    AuditService audit;
    @Autowired
    AuditLogRepository repository;

    @Test
    void writesReadableEntryForSystemWhenNobodyIsLoggedIn() {
        String ref = "T" + System.nanoTime();
        audit.log(AuditAction.CREATE, "Customer", ref, "Registered W.M. Sunil Perera");

        List<AuditLog> rows = repository.findByEntityAndRefOrderByIdAsc("Customer", ref);
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getUsername()).isEqualTo("system");
        assertThat(row.getUserId()).isNull();
        assertThat(row.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(row.getDetails()).isEqualTo("Registered W.M. Sunil Perera");
        assertThat(row.getTs()).isNotNull();
    }

    @Test
    void auditRowsCannotBeChangedOrDeletedEvenWithSql() {
        String ref = "T" + System.nanoTime();
        audit.log(AuditAction.UPDATE, "Setting", ref, "Changed");

        assertThatThrownBy(() -> jdbc.update("update audit_log set details = 'x' where ref = ?", ref))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("insert-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where ref = ?", ref))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("insert-only");
    }
}
