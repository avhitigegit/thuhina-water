package lk.thuhina.water.security.model;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Failed logins per username (also for usernames that do not exist, so the lock does not reveal which
 * usernames are real). 5 failures → locked for 15 minutes (design 8.1).
 */
@Entity
@Table(name = "login_attempt")
public class LoginAttempt {

    @Id
    @Column(length = 50)
    private String username;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "last_failed_at")
    private Instant lastFailedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected LoginAttempt() {
    }

    public LoginAttempt(String username) {
        this.username = username;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts one more failure. A finished lock, or failures older than the lock time, start the count again.
     *
     * @return true when this failure locks the username
     */
    public boolean recordFailure(Instant now, int maxAttempts, Duration lockDuration) {
        boolean lockOver = lockedUntil != null && !lockedUntil.isAfter(now);
        boolean stale = lastFailedAt != null && lastFailedAt.plus(lockDuration).isBefore(now);
        if (lockOver || stale) {
            failedCount = 0;
            lockedUntil = null;
        }
        failedCount++;
        lastFailedAt = now;
        if (failedCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public String getUsername() {
        return username;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public Instant getLastFailedAt() {
        return lastFailedAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
