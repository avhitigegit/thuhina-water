package lk.thuhina.water.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** M01 users: create, edit, deactivate / activate, reset password, and who may do it (S6). */
class UserAdminIntegrationTest extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLog;

    private static long seq = System.nanoTime() % 100000;

    private static String newUsername(String prefix) {
        return prefix + "." + (++seq);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Cookie cookie, Object body) {
        return b.cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    private Map<String, Object> newUser(String username, String role, String password) {
        Map<String, Object> m = new HashMap<>();
        m.put("fullName", "Saman Dissanayake");
        m.put("username", username);
        m.put("role", role);
        m.put("phone", " 077 123 4567 ");
        m.put("temporaryPassword", password);
        return m;
    }

    @Test
    void adminCreatesDeliveryStaffWhoMustChangePasswordAndSeesOnlyTheDailyList() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        String username = newUsername("saman.d");

        mvc.perform(json(post("/users"), admin, newUser("  " + username.toUpperCase() + " ", "DELIVERY_STAFF", "Start-123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roleName").value("Delivery Staff"))
                .andExpect(jsonPath("$.phone").value("077 123 4567"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        assertThat(lastAudit(username)).satisfies(a -> {
            assertThat(a.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(a.getDetails()).isEqualTo("Saman Dissanayake – Delivery Staff");
        });

        // The new user logs in with the temporary password, must change it, then sees only the Daily Delivery List.
        MvcResult login = loginResult(username, "Start-123");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        Cookie cookie = login.getResponse().getCookie(COOKIE);
        mvc.perform(get("/auth/me").cookie(cookie)).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/delivery/due").cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        MvcResult changed = mvc.perform(json(post("/auth/change-password"), cookie,
                        Map.of("currentPassword", "Start-123", "newPassword", "My-Own-Pass-9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingPage").value("/delivery/daily-list"))
                .andExpect(jsonPath("$.menu.length()").value(1))
                .andExpect(jsonPath("$.menu[0].items.length()").value(1))
                .andExpect(jsonPath("$.menu[0].items[0].path").value("/delivery/daily-list"))
                .andReturn();
        Cookie newCookie = changed.getResponse().getCookie(COOKIE);
        mvc.perform(get("/users").cookie(newCookie)).andExpect(status().isForbidden());
    }

    @Test
    void usernameRulesAndDuplicates() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        AppUser existing = createUser(Role.ACCOUNTANT);

        mvc.perform(json(post("/users"), admin, newUser("ab", "ACCOUNTANT", "Start-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.username")
                        .value("Username: at least 3 lowercase letters, numbers, dots or underscores."));
        mvc.perform(json(post("/users"), admin, newUser("saman d", "ACCOUNTANT", "Start-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.username")
                        .value("Username: at least 3 lowercase letters, numbers, dots or underscores."));
        mvc.perform(json(post("/users"), admin, newUser(existing.getUsername().toUpperCase(), "ACCOUNTANT", "Start-123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.username").value("Username already taken."));
        mvc.perform(json(post("/users"), admin, newUser(newUsername("short.pw"), "ACCOUNTANT", "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.temporaryPassword")
                        .value("Temporary password must be at least 8 characters."));

        Map<String, Object> missing = newUser(newUsername("no.name"), "ACCOUNTANT", "Start-123");
        missing.put("fullName", " ");
        missing.remove("role");
        mvc.perform(json(post("/users"), admin, missing))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.fullName").value("Full name is required."))
                .andExpect(jsonPath("$.details.fields.role").value("Choose a role."));
    }

    @Test
    void editingAUserAuditsTheRoleChange() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        AppUser acc = createUser(Role.ACCOUNTANT);
        String renamed = newUsername("shanika.f");

        Map<String, Object> edit = new HashMap<>();
        edit.put("fullName", "Shanika Fernando");
        edit.put("username", renamed);
        edit.put("role", "ADMIN");
        edit.put("phone", "");
        edit.put("version", acc.getVersion());
        mvc.perform(json(put("/users/" + acc.getId()), admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(renamed))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.phone").value(nullValue()));

        assertThat(lastAudit(renamed).getDetails())
                .startsWith("role Accountant → Admin; username " + acc.getUsername() + " → " + renamed + "; name ");

        // The screen still had the old version → someone else changed it.
        mvc.perform(json(put("/users/" + acc.getId()), admin, edit))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        // Saving with no change writes no audit row.
        long before = auditLog.findByEntityAndRefOrderByIdAsc("User", renamed).size();
        edit.put("version", null);
        mvc.perform(json(put("/users/" + acc.getId()), admin, edit)).andExpect(status().isOk());
        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("User", renamed)).hasSize((int) before);
    }

    @Test
    void adminCannotDeactivateOrChangeTheRoleOfTheirOwnAccount() throws Exception {
        AppUser me = createUser(Role.ADMIN);
        Cookie admin = login(me);

        mvc.perform(post("/users/" + me.getId() + "/deactivate").cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_DEACTIVATE"))
                .andExpect(jsonPath("$.message").value("You cannot deactivate your own account."));

        Map<String, Object> edit = new HashMap<>();
        edit.put("fullName", me.getFullName());
        edit.put("username", me.getUsername());
        edit.put("role", "ACCOUNTANT");
        mvc.perform(json(put("/users/" + me.getId()), admin, edit))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"));

        mvc.perform(post("/users/" + me.getId() + "/reset-password").cookie(admin))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SELF_RESET"));

        assertThat(users.findById(me.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivationLogsTheUserOutAndActivationLetsThemBackIn() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        AppUser staff = createUser(Role.DELIVERY_STAFF);
        Cookie staffCookie = login(staff);

        mvc.perform(post("/users/" + staff.getId() + "/deactivate").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/auth/me").cookie(staffCookie)).andExpect(status().isUnauthorized());
        assertThat(loginResult(staff.getUsername(), PASSWORD).getResponse().getStatus()).isEqualTo(422);
        assertThat(lastAudit(staff.getUsername()).getDetails()).isEqualTo("Deactivated");

        mvc.perform(post("/users/" + staff.getId() + "/activate").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(lastAudit(staff.getUsername()).getDetails()).isEqualTo("Activated");
        login(staff);
    }

    @Test
    void resetPasswordShowsATemporaryPasswordOnceAndEndsTheOldSession() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        AppUser acc = createUser(Role.ACCOUNTANT);
        Cookie oldSession = login(acc);
        // A login lock on the username is cleared by the reset.
        for (int i = 0; i < 5; i++) {
            loginResult(acc.getUsername(), "wrong-" + i);
        }

        MvcResult result = mvc.perform(post("/users/" + acc.getId() + "/reset-password").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String temporary = (String) json.readValue(result.getResponse().getContentAsString(), Map.class).get("temporaryPassword");
        assertThat(temporary).hasSize(10);

        mvc.perform(get("/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        assertThat(loginResult(acc.getUsername(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        MvcResult login = loginResult(acc.getUsername(), temporary);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(login.getResponse().getContentAsString()).contains("\"mustChangePassword\":true");

        assertThat(lastAudit(acc.getUsername()).getDetails()).isEqualTo("Password reset – temporary password issued");
        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("User", acc.getUsername()))
                .noneMatch(a -> a.getDetails() != null && a.getDetails().contains(temporary));
    }

    @Test
    void listShowsActiveUsersFirst() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        AppUser inactive = createUser(Role.DELIVERY_STAFF);
        inactive.setActive(false);
        users.save(inactive);

        MvcResult result = mvc.perform(get("/users").cookie(admin)).andExpect(status().isOk()).andReturn();
        List<?> list = json.readValue(result.getResponse().getContentAsString(), List.class);
        int lastActive = -1;
        int firstInactive = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Map<?, ?> u = (Map<?, ?>) list.get(i);
            if (Boolean.TRUE.equals(u.get("active"))) {
                lastActive = i;
            } else {
                firstInactive = Math.min(firstInactive, i);
            }
            assertThat(u.containsKey("passwordHash")).isFalse();
        }
        assertThat(lastActive).isLessThan(firstInactive);
    }

    @Test
    void accountantAndDeliveryStaffCannotUseAdministration() throws Exception {
        AppUser target = createUser(Role.DELIVERY_STAFF);
        for (Role role : List.of(Role.ACCOUNTANT, Role.DELIVERY_STAFF)) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/users").cookie(cookie)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(json(post("/users"), cookie, newUser(newUsername("x.y"), "ADMIN", "Start-123")))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/users/" + target.getId() + "/deactivate").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(post("/users/" + target.getId() + "/reset-password").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/audit-log").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/roles/matrix").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/settings/company").cookie(cookie)).andExpect(status().isForbidden());
        }
        assertThat(users.findById(target.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void unknownUserGives404() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        mvc.perform(post("/users/999999999/activate").cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private AuditLog lastAudit(String username) {
        List<AuditLog> rows = auditLog.findByEntityAndRefOrderByIdAsc("User", username);
        assertThat(rows).isNotEmpty();
        return rows.get(rows.size() - 1);
    }
}
