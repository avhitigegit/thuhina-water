package lk.thuhina.water.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.purchasing.model.PoStatus;
import lk.thuhina.water.purchasing.rules.PurchasingRules;
import lk.thuhina.water.sales.model.PayMethod;
import org.junit.jupiter.api.Test;

/** M09 purchasing rules without the database: PO steps, next step, lowest prices, receipt lines, payment reference. */
class PurchasingRulesTest {

    @Test
    void poStepsGoOneAtATime() {
        assertThat(PurchasingRules.canMove(PoStatus.DRAFT, PoStatus.APPROVED)).isTrue();
        assertThat(PurchasingRules.canMove(PoStatus.APPROVED, PoStatus.SENT)).isTrue();
        assertThat(PurchasingRules.canMove(PoStatus.SENT, PoStatus.PARTLY_RECEIVED)).isTrue();
        assertThat(PurchasingRules.canMove(PoStatus.SENT, PoStatus.RECEIVED)).isTrue();
        assertThat(PurchasingRules.canMove(PoStatus.PARTLY_RECEIVED, PoStatus.PARTLY_RECEIVED)).isTrue();
        assertThat(PurchasingRules.canMove(PoStatus.PARTLY_RECEIVED, PoStatus.RECEIVED)).isTrue();

        assertThat(PurchasingRules.canMove(PoStatus.DRAFT, PoStatus.SENT)).isFalse();
        assertThat(PurchasingRules.canMove(PoStatus.APPROVED, PoStatus.RECEIVED)).isFalse();
        assertThat(PurchasingRules.canMove(PoStatus.RECEIVED, PoStatus.CANCELLED)).isFalse();
        assertThat(PurchasingRules.canMove(PoStatus.CANCELLED, PoStatus.APPROVED)).isFalse();
        assertThatThrownBy(() -> PurchasingRules.checkMove(PoStatus.APPROVED, PoStatus.RECEIVED))
                .isInstanceOf(BusinessException.class).hasMessage("Cannot change Approved to Received.");
    }

    @Test
    void cancelOnlyFromDraftOrApproved() {
        assertThat(Arrays.stream(PoStatus.values()).filter(PurchasingRules::canCancel))
                .containsExactly(PoStatus.DRAFT, PoStatus.APPROVED);
        assertThat(Arrays.stream(PoStatus.values()).filter(PurchasingRules::canReceive))
                .containsExactly(PoStatus.SENT, PoStatus.PARTLY_RECEIVED);
    }

    @Test
    void nextStepShowsOnlyTheNextAction() {
        assertThat(PurchasingRules.nextStep(PoStatus.DRAFT)).isEqualTo("Approve");
        assertThat(PurchasingRules.nextStep(PoStatus.APPROVED)).isEqualTo("Mark as sent");
        assertThat(PurchasingRules.nextStep(PoStatus.SENT)).isEqualTo("Receive goods");
        assertThat(PurchasingRules.nextStep(PoStatus.PARTLY_RECEIVED)).isEqualTo("Receive the rest");
        assertThat(PurchasingRules.nextStep(PoStatus.RECEIVED)).isNull();
        assertThat(PurchasingRules.nextStep(PoStatus.CANCELLED)).isNull();
    }

    @Test
    void lowestOnlyWhenTwoOrMorePrices() {
        assertThat(PurchasingRules.lowest(List.of(new BigDecimal("620"), new BigDecimal("655"), new BigDecimal("640"))))
                .containsExactly(true, false, false);
        assertThat(PurchasingRules.lowest(Arrays.asList(new BigDecimal("410"), new BigDecimal("430"), null)))
                .containsExactly(true, false, false);
        // One price only (the others did not quote): information would be meaningless.
        assertThat(PurchasingRules.lowest(Arrays.asList(new BigDecimal("3200"), null))).containsExactly(false, false);
        assertThat(PurchasingRules.lowest(List.of(new BigDecimal("3100")))).containsExactly(false);
        // Equal lowest prices are both flagged.
        assertThat(PurchasingRules.lowest(List.of(new BigDecimal("600.00"), new BigDecimal("600"), new BigDecimal("610"))))
                .containsExactly(true, true, false);
    }

    @Test
    void receiptLines() {
        assertThat(PurchasingRules.receiptLineProblem("Empty 20L Bottle", 300, 300, 3)).isNull();
        assertThat(PurchasingRules.receiptLineProblem("Empty 20L Bottle", 300, 301, 0))
                .isEqualTo("Received for Empty 20L Bottle is more than the 300 still due.");
        assertThat(PurchasingRules.receiptLineProblem("Manual Bottle Pump", 10, 4, 5))
                .isEqualTo("Damaged cannot be more than received for Manual Bottle Pump.");
        assertThat(PurchasingRules.receiptLineProblem("Manual Bottle Pump", 10, -1, 0)).isEqualTo("Quantities cannot be negative.");

        assertThat(PurchasingRules.afterReceipt(List.of(new int[] {300, 300}, new int[] {20, 20}))).isEqualTo(PoStatus.RECEIVED);
        assertThat(PurchasingRules.afterReceipt(List.of(new int[] {300, 300}, new int[] {20, 4}))).isEqualTo(PoStatus.PARTLY_RECEIVED);
    }

    @Test
    void referenceRequiredUnlessCash() {
        assertThat(PurchasingRules.referenceMissing(PayMethod.CASH, null)).isFalse();
        assertThat(PurchasingRules.referenceMissing(PayMethod.BANK_TRANSFER, " ")).isTrue();
        assertThat(PurchasingRules.referenceMissing(PayMethod.CHEQUE, null)).isTrue();
        assertThat(PurchasingRules.referenceMissing(PayMethod.CHEQUE, "Cheque 004512")).isFalse();
    }
}
