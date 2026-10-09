package lk.thuhina.water.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import lk.thuhina.water.security.model.LoginAttempt;
import org.junit.jupiter.api.Test;

/** Lock rule: 5 failures → 15 minutes (design 8.1). */
class LoginAttemptTest {

    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final Instant T0 = Instant.parse("2026-10-05T03:00:00Z");

    @Test
    void fifthFailureLocksForFifteenMinutes() {
        LoginAttempt a = new LoginAttempt("kasun.d");
        for (int i = 0; i < 4; i++) {
            assertThat(a.recordFailure(T0.plusSeconds(i), 5, LOCK)).isFalse();
        }
        assertThat(a.recordFailure(T0.plusSeconds(5), 5, LOCK)).isTrue();
        assertThat(a.isLocked(T0.plusSeconds(6))).isTrue();
        assertThat(a.isLocked(T0.plusSeconds(5).plus(LOCK))).isFalse();
    }

    @Test
    void countStartsAgainAfterTheLockEnds() {
        LoginAttempt a = new LoginAttempt("kasun.d");
        for (int i = 0; i < 5; i++) {
            a.recordFailure(T0, 5, LOCK);
        }
        Instant later = T0.plus(LOCK).plusSeconds(1);
        assertThat(a.recordFailure(later, 5, LOCK)).isFalse();
        assertThat(a.getFailedCount()).isEqualTo(1);
        assertThat(a.isLocked(later)).isFalse();
    }

    @Test
    void oldFailuresDoNotCount() {
        LoginAttempt a = new LoginAttempt("kasun.d");
        for (int i = 0; i < 4; i++) {
            a.recordFailure(T0, 5, LOCK);
        }
        assertThat(a.recordFailure(T0.plus(Duration.ofHours(2)), 5, LOCK)).isFalse();
        assertThat(a.getFailedCount()).isEqualTo(1);
    }
}
