package lk.thuhina.water.security.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.config.AppProperties;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.RolePermissions;
import lk.thuhina.water.security.dto.ChangePasswordRequest;
import lk.thuhina.water.security.dto.LoginRequest;
import lk.thuhina.water.security.dto.MeResponse;
import lk.thuhina.water.security.model.LoginAttempt;
import lk.thuhina.water.security.repository.LoginAttemptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login, logout, "who am I" and password change (FR-56, design 8.1).
 * <ul>
 *   <li>Unknown username and wrong password give the same message, so usernames cannot be guessed.</li>
 *   <li>5 failures lock the username for 15 minutes.</li>
 *   <li>"Deactivated" is shown only after the right password, for the same reason.</li>
 * </ul>
 */
@Service
public class AuthService {

    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String ACCOUNT_INACTIVE = "ACCOUNT_INACTIVE";
    public static final String WRONG_PASSWORD = "WRONG_PASSWORD";
    public static final String SAME_PASSWORD = "SAME_PASSWORD";

    public static final String MSG_INVALID = "Invalid username or password.";
    public static final String MSG_INACTIVE = "This account is deactivated. Contact the Admin.";


    private final AppUserRepository users;
    private final LoginAttemptRepository attempts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final MenuService menu;
    private final AppProperties.Login loginRules;
    private final Clock clock;
    /** BCrypt of a random value: checked for unknown usernames so both failure paths take the same time. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, LoginAttemptRepository attempts, PasswordEncoder encoder, JwtService jwt,
                       AuditService audit, MenuService menu, AppProperties properties, Clock clock) {
        this.users = users;
        this.attempts = attempts;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.menu = menu;
        this.loginRules = properties.login();
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    /** Result of a successful login: the token for the cookie and the "me" data for the client. */
    public record LoginResult(String token, MeResponse me) {
    }

    /** Failed attempts must be saved even though the login fails, so business errors do not roll back. */
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult login(LoginRequest request) {
        String username = AppUser.normalizeUsername(request.username());
        Instant now = Instant.now(clock);

        LoginAttempt attempt = attempts.findById(username).orElse(null);
        if (attempt != null && attempt.isLocked(now)) {
            throw locked(attempt);
        }

        AppUser user = users.findByUsername(username).orElse(null);
        boolean passwordOk;
        if (user != null) {
            passwordOk = encoder.matches(request.password(), user.getPasswordHash());
        } else {
            encoder.matches(request.password(), dummyHash);
            passwordOk = false;
        }
        if (!passwordOk) {
            if (attempt == null) {
                attempt = new LoginAttempt(username);
            }
            boolean nowLocked = attempt.recordFailure(now, loginRules.maxFailedAttempts(), loginRules.lockDuration());
            attempts.save(attempt);
            if (nowLocked) {
                throw locked(attempt);
            }
            throw new AuthException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, MSG_INVALID);
        }
        if (!user.isActive()) {
            throw new BusinessException(ACCOUNT_INACTIVE, MSG_INACTIVE);
        }

        if (attempt != null) {
            attempts.delete(attempt);
        }
        user.recordLogin(now);
        AuthUser authUser = toAuthUser(user);
        audit.logFor(authUser, AuditAction.LOGIN, "Session", user.getUsername(), "Signed in");
        return new LoginResult(jwt.issue(user), me(authUser));
    }

    @Transactional
    public void logout(AuthUser user) {
        if (user != null) {
            audit.logFor(user, AuditAction.LOGOUT, "Session", user.username(), "Signed out");
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(AuthUser user) {
        return new MeResponse(
                new MeResponse.UserInfo(user.id(), user.username(), user.fullName(), user.role().name(), user.role().label()),
                user.mustChangePassword(),
                user.role().landingPage(),
                RolePermissions.of(user.role()).stream().sorted().toList(),
                menu.menuFor(user.role()));
    }

    /**
     * Changes the user's own password. Ends the user's other sessions (token version + 1) and returns a new token
     * for this session.
     */
    @Transactional
    public LoginResult changePassword(AuthUser current, ChangePasswordRequest request) {
        AppUser user = users.findById(current.id()).orElseThrow();
        if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(WRONG_PASSWORD, "Your current password is not correct.");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(SAME_PASSWORD, "The new password must be different from the old one.");
        }
        user.changePassword(encoder.encode(request.newPassword()));
        users.saveAndFlush(user);
        AuthUser updated = toAuthUser(user);
        audit.logFor(updated, AuditAction.UPDATE, "User", user.getUsername(), "Changed own password");
        return new LoginResult(jwt.issue(user), me(updated));
    }

    private BusinessException locked(LoginAttempt attempt) {
        long minutes = Math.max(1, loginRules.lockDuration().toMinutes());
        return new BusinessException(ACCOUNT_LOCKED, "Too many attempts. Try again in " + minutes + " minutes.",
                Map.of("lockedUntil", attempt.getLockedUntil().toString()));
    }

    private static AuthUser toAuthUser(AppUser u) {
        return new AuthUser(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isMustChangePassword());
    }

    /** 401 for a failed login (not a business rule – the user is simply not logged in). */
    static final class AuthException extends BusinessException {
        AuthException(HttpStatus status, String code, String message) {
            super(status, code, message, Map.of());
        }
    }
}
