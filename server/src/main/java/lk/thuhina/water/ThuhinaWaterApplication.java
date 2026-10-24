package lk.thuhina.water;

import java.util.TimeZone;

import lk.thuhina.water.common.BusinessDates;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ThuhinaWaterApplication {

    public static void main(String[] args) {
        // Server and database run in Sri Lanka time (design 2).
        TimeZone.setDefault(TimeZone.getTimeZone(BusinessDates.ZONE));
        SpringApplication.run(ThuhinaWaterApplication.class, args);
    }
}
