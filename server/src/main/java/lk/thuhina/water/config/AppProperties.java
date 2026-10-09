package lk.thuhina.water.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings from {@code application*.yml} / environment variables (design 12.1).
 * Secrets (JWT secret, admin temporary password) come only from the environment, never from Git.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cookie cookie, @DefaultValue Login login, @DefaultValue Bootstrap bootstrap) {

    /** JWT signing secret (at least 32 characters) and lifetime – 12 hours = one working day (design 8.1). */
    public record Jwt(String secret, @DefaultValue("12h") Duration ttl) {
    }

    /** Auth cookie. {@code secure} is switched off only in the local profile (plain http). */
    public record Cookie(@DefaultValue("TW_AUTH") String name, @DefaultValue("true") boolean secure) {
    }

    /** 5 failed logins lock the username for 15 minutes (design 8.1). */
    public record Login(@DefaultValue("5") int maxFailedAttempts, @DefaultValue("15m") Duration lockDuration) {
    }

    /** First-run Admin, created only when the user table is empty (ADMIN_USERNAME, ADMIN_TEMP_PASSWORD). */
    public record Bootstrap(String adminUsername, String adminTempPassword,
                            @DefaultValue("System Administrator") String adminFullName) {
    }
}
