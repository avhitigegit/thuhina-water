package lk.thuhina.water.quotations;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lk.thuhina.water.quotations.rules.CustomerQuotationRules;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Kind;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Line;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Status;
import org.junit.jupiter.api.Test;

/** M16 rules (FR-64, FR-65, BR-20): only the next step, Expired derived, next step text, validations in the prototype's order. */
class CustomerQuotationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final List<Line> ONE = List.of(new Line(Kind.WATER, "20L water", 40, new BigDecimal("285")));

    @Test
    void onlyTheNextStep() {
        assertThat(CustomerQuotationRules.stepProblem(Status.DRAFT, Status.SENT)).isNull();
        assertThat(CustomerQuotationRules.stepProblem(Status.SENT, Status.ACCEPTED)).isNull();
        assertThat(CustomerQuotationRules.stepProblem(Status.SENT, Status.REJECTED)).isNull();
        assertThat(CustomerQuotationRules.stepProblem(Status.DRAFT, Status.ACCEPTED)).isEqualTo("Cannot change Draft to Accepted.");
        assertThat(CustomerQuotationRules.stepProblem(Status.SENT, Status.SENT)).isEqualTo("Cannot change Sent to Sent.");
        assertThat(CustomerQuotationRules.stepProblem(Status.ACCEPTED, Status.REJECTED)).isEqualTo("Cannot change Accepted to Not accepted.");
    }

    @Test
    void aSentQuotationPastItsDateShowsExpired() {
        assertThat(CustomerQuotationRules.shown(Status.SENT, TODAY.minusDays(1), TODAY)).isEqualTo("EXPIRED");
        assertThat(CustomerQuotationRules.shown(Status.SENT, TODAY, TODAY)).isEqualTo("SENT");
        assertThat(CustomerQuotationRules.shown(Status.DRAFT, TODAY.minusDays(9), TODAY)).isEqualTo("DRAFT");
        assertThat(CustomerQuotationRules.shown(Status.ACCEPTED, TODAY.minusDays(9), TODAY)).isEqualTo("ACCEPTED");
        assertThat(CustomerQuotationRules.shownLabel("EXPIRED")).isEqualTo("Expired");
        assertThat(CustomerQuotationRules.shownLabel("REJECTED")).isEqualTo("Not accepted");
    }

    @Test
    void nextStepAsInThePrototype() {
        assertThat(CustomerQuotationRules.nextStep("DRAFT", false, false, true)).isEqualTo("Send to the customer");
        assertThat(CustomerQuotationRules.nextStep("SENT", false, false, true)).isEqualTo("Record the answer");
        assertThat(CustomerQuotationRules.nextStep("EXPIRED", false, false, true)).isEqualTo("Record the answer or make a new copy");
        assertThat(CustomerQuotationRules.nextStep("ACCEPTED", false, false, true)).isEqualTo("Register as customer");
        assertThat(CustomerQuotationRules.nextStep("ACCEPTED", true, false, true)).isEqualTo("Apply prices to customer");
        assertThat(CustomerQuotationRules.nextStep("ACCEPTED", true, true, true)).isEqualTo("–");
        assertThat(CustomerQuotationRules.nextStep("ACCEPTED", true, false, false)).isEqualTo("–");
        assertThat(CustomerQuotationRules.nextStep("REJECTED", false, false, true)).isEqualTo("–");
    }

    @Test
    void validationsInThePrototypeOrder() {
        assertThat(CustomerQuotationRules.problem(false, "Divisional Secretariat", TODAY, TODAY.plusDays(30), true, ONE)).isNull();
        assertThat(CustomerQuotationRules.problem(true, null, TODAY, TODAY, true, ONE)).isNull();
        assertThat(CustomerQuotationRules.problem(false, " ", TODAY, TODAY.plusDays(30), true, ONE)).isEqualTo(CustomerQuotationRules.MSG_TO);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY.minusDays(1), true, ONE)).isEqualTo(CustomerQuotationRules.MSG_VALID);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY, false, ONE)).isEqualTo(CustomerQuotationRules.MSG_TYPE);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY, true, List.of())).isEqualTo(CustomerQuotationRules.MSG_NO_LINES);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY, true, List.of(new Line(Kind.OTHER, "Installation", 0, BigDecimal.ONE))))
                .isEqualTo(CustomerQuotationRules.MSG_LINE);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY, true, List.of(new Line(Kind.PRODUCT, " ", 1, BigDecimal.ONE))))
                .isEqualTo(CustomerQuotationRules.MSG_LINE);
        assertThat(CustomerQuotationRules.problem(false, "X", TODAY, TODAY, true, List.of(new Line(Kind.OTHER, "Discount", 1, new BigDecimal("-5")))))
                .isEqualTo(CustomerQuotationRules.MSG_PRICE);
        assertThat(CustomerQuotationRules.dateProblem(TODAY.plusDays(1), TODAY)).isEqualTo("Date cannot be after today (14/10/2026).");
        assertThat(CustomerQuotationRules.amount(40, new BigDecimal("285"))).isEqualByComparingTo("11400");
    }
}
