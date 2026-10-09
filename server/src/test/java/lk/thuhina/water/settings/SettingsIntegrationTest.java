package lk.thuhina.water.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SettingsIntegrationTest extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLog;

    @Test
    void adminReadsCompanyDetails() throws Exception {
        Cookie cookie = login(createUser(Role.ADMIN));
        mvc.perform(get("/settings/company").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Thuhina Water (Pvt) Ltd"))
                .andExpect(jsonPath("$.regNo").value("PV 00231877"));
    }

    @Test
    void adminChangesBusinessSettingAndItIsAudited() throws Exception {
        AppUser admin = createUser(Role.ADMIN);
        Cookie cookie = login(admin);
        mvc.perform(put("/settings/business").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("dateFormat", "  DD/MM/YYYY  ", "currency", "LKR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dateFormat").value("DD/MM/YYYY"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Colombo"));

        // Nothing changed (values trimmed and equal) → no audit row.
        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("Setting", "business"))
                .noneMatch(a -> admin.getUsername().equals(a.getUsername()));
    }

    @Test
    void featureSwitchShowsTheDataMigrationMenu() throws Exception {
        AppUser admin = createUser(Role.ADMIN);
        Cookie cookie = login(admin);
        try {
            mvc.perform(put("/settings/features").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("dataMigration", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dataMigration").value(true));
            mvc.perform(get("/auth/me").cookie(cookie))
                    .andExpect(jsonPath("$.menu[?(@.module == 'Data Migration')].hidden").value(contains(false)));
            assertThat(auditLog.findByEntityAndRefOrderByIdAsc("Setting", "features"))
                    .anyMatch(a -> a.getAction() == AuditAction.UPDATE && admin.getUsername().equals(a.getUsername())
                            && a.getDetails().equals("Changed features settings: dataMigration"));
        } finally {
            mvc.perform(put("/settings/features").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("dataMigration", false)))).andExpect(status().isOk());
        }
    }

    @Test
    void unknownKeysAndWrongTypesAreRefused() throws Exception {
        Cookie cookie = login(createUser(Role.ADMIN));
        mvc.perform(put("/settings/features").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataMigration\": \"yes\", \"other\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.details.fields.dataMigration").value("Must be true or false."))
                .andExpect(jsonPath("$.details.fields.other").value("Unknown setting."));
    }

    @Test
    void unknownGroupGives404() throws Exception {
        Cookie cookie = login(createUser(Role.ADMIN));
        mvc.perform(get("/settings/nothing").cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
