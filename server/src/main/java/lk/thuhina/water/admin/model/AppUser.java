package lk.thuhina.water.admin.model;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.security.Role;

/**
 * A system user (design 4.3 {@code app_user}). Created in M00 for login; users are managed in M01.
 * {@code tokenVersion} is part of every JWT: raising it logs the user out at once.
 */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    protected AppUser() {
    }

    public AppUser(String username, String fullName, String phone, Role role, String passwordHash, boolean mustChangePassword) {
        this.username = normalizeUsername(username);
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
    }

    /** Usernames are stored and compared in lower case. */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /** The user's own password change: clears the forced change and ends every other session. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.mustChangePassword = false;
        this.tokenVersion++;
    }

    /** Logs the user out everywhere (deactivation, password reset). */
    public void invalidateSessions() {
        this.tokenVersion++;
    }

    public void setActive(boolean active) {
        if (this.active && !active) {
            invalidateSessions();
        }
        this.active = active;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
