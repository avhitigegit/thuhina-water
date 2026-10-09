package lk.thuhina.water.security.service;

import java.time.Clock;
import java.time.Instant;

import lk.thuhina.water.config.AppProperties;
import lk.thuhina.water.security.repository.LoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Hourly: removes finished login locks and old failure counts (design 9.1). */
@Component
public class LoginLockCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(LoginLockCleanupJob.class);

    private final LoginAttemptRepository attempts;
    private final AppProperties.Login rules;
    private final Clock clock;

    public LoginLockCleanupJob(LoginAttemptRepository attempts, AppProperties properties, Clock clock) {
        this.attempts = attempts;
        this.rules = properties.login();
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Colombo")
    @Transactional
    public void run() {
        Instant now = Instant.now(clock);
        int removed = attempts.deleteExpired(now, now.minus(rules.lockDuration()));
        if (removed > 0) {
            log.info("Login lock clean-up removed {} row(s)", removed);
        }
    }
}
