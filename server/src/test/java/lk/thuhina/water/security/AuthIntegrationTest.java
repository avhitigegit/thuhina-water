package lk.thuhina.water.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLog;

    @Test
    @SuppressWarnings("unchecked")
    void loginSetsSecureHttpOnlyCookieAndReturnsUserPermissionsAndMenu() throws Exception {
        AppUser admin = createUser(Role.ADMIN);

        MvcResult result = loginResult(admin.getUsername().toUpperCase(), PASSWORD); // username is not case-sensitive

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(COOKIE + "=").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Strict").contains("Max-Age=43200").contains("Path=/");
        Map<?, ?> me = json.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(me.get("landingPage")).isEqualTo("/dashboard");
        assertThat(me.get("mustChangePassword")).isEqualTo(false);
        assertThat((java.util.List<Object>) me.get("permissions")).contains((Object) Permissions.ADMIN_MANAGE, (Object) Permissions.SALES_APPROVE_OVER_LIMIT);

        AppUser reloaded = users.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(auditLog.findByUsernameAndActionOrderByIdAsc(admin.getUsername(), AuditAction.LOGIN)).hasSize(1);
    }

    @Test
    void meReturnsTheLoggedInUser() throws Exception {
        AppUser acc = createUser(Role.ACCOUNTANT);
        Cookie cookie = login(acc);

        mvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(acc.getUsername()))
                .andExpect(jsonPath("$.user.role").value("ACCOUNTANT"))
                .andExpect(jsonPath("$.user.roleName").value("Accountant"));
    }

    @Test
    void unknownUserAndWrongPasswordGiveTheSameMessage() throws Exception {
        AppUser user = createUser(Role.ADMIN);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "nobody.here", "password", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void fiveFailedAttemptsLockTheAccountFor15Minutes() throws Exception {
        AppUser user = createUser(Role.ACCOUNTANT);
        for (int i = 1; i <= 4; i++) {
            assertThat(loginResult(user.getUsername(), "bad-" + i).getResponse().getStatus()).isEqualTo(401);
        }

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", "bad-5"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value("Too many attempts. Try again in 15 minutes."));

        // Even the right password is refused while locked.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        // After the 15 minutes the right password works again and the counter is cleared.
        jdbc.update("update login_attempt set locked_until = now() - interval '1 minute' where username = ?", user.getUsername());
        login(user);
        Integer rows = jdbc.queryForObject("select count(*) from login_attempt where username = ?", Integer.class, user.getUsername());
        assertThat(rows).isZero();
    }

    @Test
    void unknownUsernamesAreLockedToo() throws Exception {
        for (int i = 1; i <= 5; i++) {
            loginResult("ghost.user", "bad-" + i);
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "ghost.user", "password", "bad"))))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        AppUser user = createUser(Role.DELIVERY_STAFF);
        user.setActive(false);
        users.save(user);

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", PASSWORD))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("This account is deactivated. Contact the Admin."));

        // With a wrong password the account state is not revealed.
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", user.getUsername(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void blankLoginGivesValidationErrorWithFieldMessages() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("Please check the highlighted fields."))
                .andExpect(jsonPath("$.details.fields.username").value("Enter your username."))
                .andExpect(jsonPath("$.details.fields.password").value("Enter your password."));
    }

    @Test
    void logoutClearsTheCookieAndIsAudited() throws Exception {
        AppUser user = createUser(Role.ACCOUNTANT);
        Cookie cookie = login(user);

        MvcResult result = mvc.perform(post("/auth/logout").cookie(cookie)).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains(COOKIE + "=;").contains("Max-Age=0");
        assertThat(auditLog.findByUsernameAndActionOrderByIdAsc(user.getUsername(), AuditAction.LOGOUT)).hasSize(1);
    }

    @Test
    void notLoggedInGives401InTheStandardFormat() throws Exception {
        mvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Please log in."));
    }

    @Test
    void tamperedOrForeignTokenIsRejected() throws Exception {
        mvc.perform(get("/auth/me").cookie(new Cookie(COOKIE, "abc.def.ghi"))).andExpect(status().isUnauthorized());

        Cookie cookie = login(createUser(Role.ADMIN));
        String[] parts = cookie.getValue().split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        mvc.perform(get("/auth/me").cookie(new Cookie(COOKIE, forged))).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivationLogsTheUserOutImmediately() throws Exception {
        AppUser user = createUser(Role.ACCOUNTANT);
        Cookie cookie = login(user);
        mvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isOk());

        AppUser u = users.findById(user.getId()).orElseThrow();
        u.setActive(false);
        users.save(u);

        mvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetByAdminLogsTheUserOut() throws Exception {
        AppUser user = createUser(Role.ADMIN);
        Cookie cookie = login(user);

        AppUser u = users.findById(user.getId()).orElseThrow();
        u.invalidateSessions();
        users.save(u);

        mvc.perform(get("/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void temporaryPasswordForcesAPasswordChange() throws Exception {
        AppUser user = createUser(Role.ADMIN, true);
        MvcResult result = loginResult(user.getUsername(), PASSWORD);
        assertThat(result.getResponse().getContentAsString()).contains("\"mustChangePassword\":true");
        Cookie oldCookie = result.getResponse().getCookie(COOKIE);

        // Nothing else can be used until the password is changed.
        mvc.perform(get("/settings/company").cookie(oldCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/auth/me").cookie(oldCookie)).andExpect(status().isOk());

        mvc.perform(post("/auth/change-password").cookie(oldCookie).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.newPassword").value("The new password must be at least 8 characters."));
        mvc.perform(post("/auth/change-password").cookie(oldCookie).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "not-my-password", "newPassword", "Brand-New-1"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mvc.perform(post("/auth/change-password").cookie(oldCookie).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));

        MvcResult changed = mvc.perform(post("/auth/change-password").cookie(oldCookie).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "Brand-New-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn();
        Cookie newCookie = changed.getResponse().getCookie(COOKIE);

        mvc.perform(get("/settings/company").cookie(newCookie)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").cookie(oldCookie)).andExpect(status().isUnauthorized()); // old session ended
        assertThat(loginResult(user.getUsername(), "Brand-New-1").getResponse().getStatus()).isEqualTo(200);
        assertThat(auditLog.findByEntityAndRefOrderByIdAsc("User", user.getUsername()))
                .anyMatch(a -> a.getAction() == AuditAction.UPDATE && a.getDetails().equals("Changed own password"));
    }
}
