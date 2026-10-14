package lk.thuhina.water.expenses;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.expenses.rules.ExpenseRules.Input;
import lk.thuhina.water.sales.model.PayMethod;
import org.junit.jupiter.api.Test;

/** M15 rules (FR-62, BR-19): messages in the prototype's order, the for-month, references, months offered, summary range. */
class ExpenseRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final Input OK = new Input(TODAY.minusDays(12), YearMonth.of(2026, 9), true, new BigDecimal("4260"), PayMethod.CASH, null);

    private static String message(Input in) {
        ExpenseRules.Problem p = ExpenseRules.problem(in, TODAY);
        return p == null ? null : p.field() + ": " + p.message();
    }

    @Test
    void aBillPaidInOctoberCanCountForSeptember() {
        assertThat(message(OK)).isNull();
        assertThat(ExpenseRules.paidInOtherMonth(LocalDate.of(2026, 10, 2), YearMonth.of(2026, 9))).isTrue();
        assertThat(ExpenseRules.paidInOtherMonth(LocalDate.of(2026, 9, 2), YearMonth.of(2026, 9))).isFalse();
    }

    @Test
    void checksInThePrototypeOrder() {
        assertThat(message(new Input(null, null, false, null, null, null))).isEqualTo("paidDate: Enter a valid date (DD/MM/YYYY).");
        assertThat(message(new Input(TODAY.plusDays(1), OK.forMonth(), true, OK.amount(), PayMethod.CASH, null)))
                .isEqualTo("paidDate: Date cannot be after today (14/10/2026).");
        assertThat(message(new Input(TODAY, null, true, OK.amount(), PayMethod.CASH, null))).isEqualTo("forMonth: " + ExpenseRules.MSG_MONTH);
        assertThat(message(new Input(TODAY, YearMonth.of(2026, 11), true, OK.amount(), PayMethod.CASH, null)))
                .isEqualTo("forMonth: " + ExpenseRules.MSG_MONTH_FUTURE);
        assertThat(message(new Input(TODAY, OK.forMonth(), false, OK.amount(), PayMethod.CASH, null))).isEqualTo("typeId: " + ExpenseRules.MSG_TYPE);
        assertThat(message(new Input(TODAY, OK.forMonth(), true, BigDecimal.ZERO, PayMethod.CASH, null)))
                .isEqualTo("amount: Enter the amount paid.");
        assertThat(message(new Input(TODAY, OK.forMonth(), true, OK.amount(), null, null))).isEqualTo("method: Select the payment method.");
    }

    @Test
    void chequeAndBankTransferNeedTheReference() {
        for (PayMethod m : new PayMethod[] {PayMethod.CHEQUE, PayMethod.BANK_TRANSFER}) {
            assertThat(message(new Input(TODAY, OK.forMonth(), true, OK.amount(), m, " ")))
                    .isEqualTo("reference: Enter the cheque number or bank reference.");
            assertThat(message(new Input(TODAY, OK.forMonth(), true, OK.amount(), m, "Cheque 330145"))).isNull();
        }
    }

    @Test
    void monthsRangesAndLabels() {
        assertThat(ExpenseRules.parseMonth("2026-09")).isEqualTo(YearMonth.of(2026, 9));
        assertThat(ExpenseRules.parseMonth("2026-09-14")).isEqualTo(YearMonth.of(2026, 9));
        assertThat(ExpenseRules.parseMonth("2026-13")).isNull();
        assertThat(ExpenseRules.parseMonth("Sept")).isNull();
        assertThat(ExpenseRules.monthLabel(YearMonth.of(2026, 9))).isEqualTo("September 2026");
        // This month and the 11 before; an older first expense adds its months.
        assertThat(ExpenseRules.months(null, TODAY)).hasSize(12).startsWith(YearMonth.of(2026, 10)).endsWith(YearMonth.of(2025, 11));
        assertThat(ExpenseRules.months(YearMonth.of(2025, 7), TODAY)).hasSize(16).endsWith(YearMonth.of(2025, 7));
        assertThat(ExpenseRules.rangeProblem(YearMonth.of(2026, 10), YearMonth.of(2026, 9))).isEqualTo(ExpenseRules.MSG_RANGE);
        assertThat(ExpenseRules.rangeProblem(YearMonth.of(2023, 11), YearMonth.of(2026, 10))).isNull();
        assertThat(ExpenseRules.rangeProblem(YearMonth.of(2023, 10), YearMonth.of(2026, 10))).isEqualTo(ExpenseRules.MSG_RANGE_LONG);
        assertThat(ExpenseRules.range(YearMonth.of(2026, 8), YearMonth.of(2026, 10))).hasSize(3);
    }
}
