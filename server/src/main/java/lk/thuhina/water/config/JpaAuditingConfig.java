package lk.thuhina.water.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import lk.thuhina.water.security.CurrentUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Fills created_by / updated_by with the logged-in username ("system" for start-up jobs) and the times from the clock. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.of(CurrentUser.usernameOrSystem());
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
