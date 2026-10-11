package lk.thuhina.water.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import lk.thuhina.water.delivery.rules.ScheduleRules;
import org.junit.jupiter.api.Test;

/** Delivery schedule rules (design 5.4, BR-16, FR-46) with weekly, 2-weekly and 4-weekly cycles. */
class ScheduleRulesTest {

    private static final LocalDate FRI_9_OCT = LocalDate.of(2026, 10, 9);   // a Friday
    private static final LocalDate MON_12_OCT = LocalDate.of(2026, 10, 12);

    @Test
    void firstDeliveryIsTheNextDeliveryDayAfterRegistration() {
        assertThat(ScheduleRules.firstDelivery(FRI_9_OCT, DayOfWeek.MONDAY)).isEqualTo(MON_12_OCT);
        // Registered on the delivery day itself → the following week.
        assertThat(ScheduleRules.firstDelivery(MON_12_OCT, DayOfWeek.MONDAY)).isEqualTo(LocalDate.of(2026, 10, 19));
        // Over a month end.
        assertThat(ScheduleRules.firstDelivery(LocalDate.of(2026, 10, 30), DayOfWeek.TUESDAY)).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(ScheduleRules.onOrAfter(MON_12_OCT, DayOfWeek.MONDAY)).isEqualTo(MON_12_OCT);
    }

    @Test
    void dueDatesFollowTheCycleBeforeAndAfterTheNextDelivery() {
        LocalDate reg = LocalDate.of(2026, 1, 5);
        // Weekly: every Monday.
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 1, MON_12_OCT, LocalDate.of(2026, 10, 19))).isTrue();
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 1, MON_12_OCT, LocalDate.of(2026, 10, 13))).isFalse();
        // Two-weekly: 12/10, 26/10, 09/11 … and 28/09 before it; not 19/10.
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 2, MON_12_OCT, LocalDate.of(2026, 10, 26))).isTrue();
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 2, MON_12_OCT, LocalDate.of(2026, 9, 28))).isTrue();
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 2, MON_12_OCT, LocalDate.of(2026, 10, 19))).isFalse();
        // Four-weekly across a month boundary: 12/10 → 09/11.
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 4, MON_12_OCT, LocalDate.of(2026, 11, 9))).isTrue();
        assertThat(ScheduleRules.isDue(true, reg, DayOfWeek.MONDAY, 4, MON_12_OCT, LocalDate.of(2026, 11, 2))).isFalse();
        // Inactive or before registration → never due.
        assertThat(ScheduleRules.isDue(false, reg, DayOfWeek.MONDAY, 1, MON_12_OCT, MON_12_OCT)).isFalse();
        assertThat(ScheduleRules.isDue(true, MON_12_OCT, DayOfWeek.MONDAY, 1, MON_12_OCT, LocalDate.of(2026, 10, 5))).isFalse();
    }

    @Test
    void nextAlignedDateAfterADay() {
        assertThat(ScheduleRules.nextAligned(MON_12_OCT, 1, FRI_9_OCT)).isEqualTo(MON_12_OCT);
        assertThat(ScheduleRules.nextAligned(MON_12_OCT, 1, MON_12_OCT)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(ScheduleRules.nextAligned(MON_12_OCT, 2, LocalDate.of(2026, 10, 13))).isEqualTo(LocalDate.of(2026, 10, 26));
        // A next date long in the past keeps its phase: 2-weekly from 03/08 → after 09/10 is 12/10.
        assertThat(ScheduleRules.nextAligned(LocalDate.of(2026, 8, 3), 2, FRI_9_OCT)).isEqualTo(MON_12_OCT);
        assertThat(ScheduleRules.nextAligned(LocalDate.of(2026, 8, 3), 4, FRI_9_OCT)).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void advanceMovesPastTheBillDateButNotBack() {
        assertThat(ScheduleRules.advance(MON_12_OCT, 1, MON_12_OCT)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(ScheduleRules.advance(MON_12_OCT, 2, LocalDate.of(2026, 10, 14))).isEqualTo(LocalDate.of(2026, 10, 26));
        // A back-dated bill before the next delivery does not move it.
        assertThat(ScheduleRules.advance(MON_12_OCT, 1, FRI_9_OCT)).isEqualTo(MON_12_OCT);
        // Several missed weeks: jumps to after the bill.
        assertThat(ScheduleRules.advance(LocalDate.of(2026, 9, 7), 1, MON_12_OCT)).isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void bottlesPerMonth() {
        assertThat(ScheduleRules.bottlesPerMonth(10, 1)).isEqualTo(43);
        assertThat(ScheduleRules.bottlesPerMonth(6, 1)).isEqualTo(26);
        assertThat(ScheduleRules.bottlesPerMonth(2, 2)).isEqualTo(4);
        assertThat(ScheduleRules.bottlesPerMonth(0, 1)).isZero();
    }
}
