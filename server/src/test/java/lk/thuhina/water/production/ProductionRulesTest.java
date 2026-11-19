package lk.thuhina.water.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

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
    void aLoadFillsTheOldestBatchesFirst() {
        // 5 left from the first batch + 100 of the new one come back as 103 filled and 2 rejected.
        List<int[]> split = ProductionRules.allocate(List.of(5, 100), 103, 2);
        assertThat(split).containsExactly(new int[] {5, 0}, new int[] {98, 2});
        // Only part of the load: the newest batch keeps the rest at the factory.
        assertThat(ProductionRules.allocate(List.of(5, 100), 95, 0)).containsExactly(new int[] {5, 0}, new int[] {90, 0});
        assertThat(ProductionRules.allocate(List.of(5, 100), 0, 3)).containsExactly(new int[] {0, 3}, new int[] {0, 0});
    }

    @Test
    void factoryPaymentsClearTheOldestMonthFirst() {
        List<BigDecimal> costs = List.of(new BigDecimal("2400"), new BigDecimal("1800"), new BigDecimal("3000"));
        assertThat(ProductionRules.spreadOldestFirst(costs, new BigDecimal("3000"))).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2400"), new BigDecimal("600"), BigDecimal.ZERO);
        // Paid ahead: the extra sits on the newest month.
        assertThat(ProductionRules.spreadOldestFirst(costs, new BigDecimal("8000"))).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("2400"), new BigDecimal("1800"), new BigDecimal("3800"));
        assertThat(ProductionRules.spreadOldestFirst(costs, BigDecimal.ZERO)).allMatch(b -> b.signum() == 0);
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
