package lk.thuhina.water.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import lk.thuhina.water.production.rules.ProductionRules;
import org.junit.jupiter.api.Test;

/** M10 filling factory rules without the database: batch status, return checks, cost, payment status, months. */
class ProductionRulesTest {

    @Test
    void batchStatusFollowsWhatCameBack() {
        assertThat(ProductionRules.batchStatus(50, 0, 0)).isEqualTo(ProductionRules.AT_FACTORY);
        assertThat(ProductionRules.batchStatus(50, 30, 2)).isEqualTo(ProductionRules.PARTLY_RETURNED);
        assertThat(ProductionRules.batchStatus(50, 48, 2)).isEqualTo(ProductionRules.RETURNED);
        assertThat(ProductionRules.stillAtFactory(50, 30, 2)).isEqualTo(18);
        assertThat(ProductionRules.batchStatusLabel(ProductionRules.PARTLY_RETURNED)).isEqualTo("Partly returned");
    }

    @Test
    void returnChecks() {
        assertThat(ProductionRules.returnProblem(18, 18, 0, null)).isNull();
        assertThat(ProductionRules.returnProblem(18, 10, 2, "Seal")).isNull();
        assertThat(ProductionRules.returnProblem(18, 0, 0, null)).isEqualTo("Enter filled and/or rejected bottles received.");
        assertThat(ProductionRules.returnProblem(6, 6, 1, "Seal")).isEqualTo("Only 6 bottles of this batch are still at the factory.");
        assertThat(ProductionRules.returnProblem(18, 10, 2, " ")).isEqualTo("Enter the reason for the rejected bottles.");
        assertThat(ProductionRules.returnProblem(18, -1, 2, "Seal")).isEqualTo("Quantities cannot be negative.");
    }

    @Test
    void costIsFilledTimesChargeRejectsFree() {
        assertThat(ProductionRules.cost(297, new BigDecimal("60"))).isEqualByComparingTo("17820.00");
        assertThat(ProductionRules.cost(0, new BigDecimal("60"))).isEqualByComparingTo("0");
    }

    @Test
    void paymentStatusAndMonths() {
        assertThat(ProductionRules.paymentStatus(new BigDecimal("1000"), BigDecimal.ZERO)).isEqualTo(ProductionRules.UNPAID);
        assertThat(ProductionRules.paymentStatus(new BigDecimal("1000"), new BigDecimal("400"))).isEqualTo(ProductionRules.PART_PAID);
        assertThat(ProductionRules.paymentStatus(new BigDecimal("1000"), new BigDecimal("999.99"))).isEqualTo(ProductionRules.PAID);
        assertThat(ProductionRules.paymentStatus(new BigDecimal("1000"), new BigDecimal("1200"))).isEqualTo(ProductionRules.PAID);
        assertThat(ProductionRules.periodLabel("2026-08")).isEqualTo("August 2026");
        assertThat(ProductionRules.isPeriod("2026-08")).isTrue();
        assertThat(ProductionRules.isPeriod("2026-13")).isFalse();
        assertThat(ProductionRules.isPeriod("August")).isFalse();
    }
}
