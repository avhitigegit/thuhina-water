package lk.thuhina.water;

import static org.assertj.core.api.Assertions.assertThat;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ThuhinaWaterApplicationTests extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLog;

    @Test
    void contextLoadsAndMigrationsRun() {
        Integer sequences = jdbc.queryForObject("select count(*) from doc_sequence", Integer.class);
        assertThat(sequences).isEqualTo(21); // 20 from V001 + DAMAGE (V005)
        String features = jdbc.queryForObject("select value::text from app_setting where key = 'features'", String.class);
        assertThat(features).contains("\"dataMigration\": false");
    }

    /** The test profile starts on an empty database with ADMIN_USERNAME / ADMIN_TEMP_PASSWORD set (application-test.yml). */
    @Test
    void firstAdminIsCreatedOnEmptyDatabaseAndMustChangePassword() {
        AppUser admin = users.findByUsername("first.admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(encoder.matches("Temp-Pass-123", admin.getPasswordHash())).isTrue();
        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("User", "first.admin"))
                .anyMatch(a -> a.getAction() == AuditAction.CREATE && "system".equals(a.getUsername()));
    }
}
