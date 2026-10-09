package lk.thuhina.water.config;

import java.time.Clock;

import lk.thuhina.water.common.BusinessDates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One clock for the whole app, in Asia/Colombo. Services take "now" and "today" from it (tests can replace it). */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(BusinessDates.ZONE);
    }
}
