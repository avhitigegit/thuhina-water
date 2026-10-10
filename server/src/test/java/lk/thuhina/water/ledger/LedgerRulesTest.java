package lk.thuhina.water.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.ledger.rules.LedgerRules.Line;
import org.junit.jupiter.api.Test;

/** BR-12 message and effect helpers. */
class LedgerRulesTest {

    @Test
    void onlyLinesThatGoBelowZeroAreProblems() {
        List<String> problems = LedgerRules.problems(List.of(
                new Line("Filled in store (20L)", 5, -8),
                new Line("Written off (20L)", 46, 8),
                new Line("Empty in store (10L)", 3, -3)));
        assertThat(problems).containsExactly("Filled in store (20L) cannot go below zero – available 5, needed 8.");
        assertThat(LedgerRules.message(problems))
                .isEqualTo("Filled in store (20L) cannot go below zero – available 5, needed 8. Record a stock adjustment first.");
    }

    @Test
    void labelsAndNegation() {
        assertThat(LedgerRules.bottleLabel(new BigDecimal("20.00"))).isEqualTo("20L");
        assertThat(LedgerRules.bottleLabel(new BigDecimal("0.50"))).isEqualTo("0.5L");
        assertThat(Bucket.FILLED.label()).isEqualTo("Filled in store");
        assertThat(Bucket.WRITTEN_OFF.inCirculation()).isFalse();
        assertThat(Effect.stock("B20", Bucket.FILLED, -3).negated()).isEqualTo(Effect.stock("B20", Bucket.FILLED, 3));
    }
}
