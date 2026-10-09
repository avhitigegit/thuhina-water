package lk.thuhina.water.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class MoneyAndDatesTest {

    @Test
    void moneyIsRoundedHalfUpToTwoDecimals() {
        assertThat(Money.of("1350")).isEqualByComparingTo("1350.00");
        assertThat(Money.of("10.005")).isEqualTo(new BigDecimal("10.01"));
        assertThat(Money.of("10.004")).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.of((BigDecimal) null)).isEqualTo(new BigDecimal("0.00"));
        assertThat(Money.times(new BigDecimal("350"), 3)).isEqualTo(new BigDecimal("1050.00"));
    }

    @Test
    void moneyDisplayFormat() {
        assertThat(Money.format(new BigDecimal("1350"))).isEqualTo("Rs. 1,350.00");
        assertThat(Money.format(new BigDecimal("1234567.5"))).isEqualTo("Rs. 1,234,567.50");
        assertThat(Money.format(new BigDecimal("-700"))).isEqualTo("Rs. -700.00");
    }

    @Test
    void todayIsInSriLankaTime() {
        // 20:00 UTC on 4 Oct is already 01:30 on 5 Oct in Colombo (UTC+05:30).
        Clock clock = Clock.fixed(Instant.parse("2026-10-04T20:00:00Z"), ZoneOffset.UTC);
        assertThat(BusinessDates.today(clock)).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void displayDateIsDdMmYyyy() {
        assertThat(BusinessDates.format(LocalDate.of(2026, 10, 5))).isEqualTo("05/10/2026");
        assertThat(BusinessDates.format(null)).isEmpty();
        assertThat(BusinessDates.firstOfMonth(LocalDate.of(2026, 10, 25))).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
