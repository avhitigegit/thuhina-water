package lk.thuhina.water.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import lk.thuhina.water.numbering.model.ResetRule;
import lk.thuhina.water.numbering.service.SequenceFormat;
import org.junit.jupiter.api.Test;

/** Number formats from design 5.3. */
class SequenceFormatTest {

    private static final LocalDate OCT_5 = LocalDate.of(2026, 10, 5);

    @Test
    void periodKeys() {
        assertThat(SequenceFormat.periodKey(ResetRule.NONE, OCT_5)).isEmpty();
        assertThat(SequenceFormat.periodKey(ResetRule.YEARLY, OCT_5)).isEqualTo("2026");
        assertThat(SequenceFormat.periodKey(ResetRule.MONTHLY, OCT_5)).isEqualTo("2610");
        assertThat(SequenceFormat.periodKey(ResetRule.MONTHLY, LocalDate.of(2027, 1, 31))).isEqualTo("2701");
    }

    @Test
    void neverResettingFormats() {
        assertThat(SequenceFormat.format("C", ResetRule.NONE, "", 1, 4)).isEqualTo("C0001");
        assertThat(SequenceFormat.format("P", ResetRule.NONE, "", 6, 2)).isEqualTo("P06");
        assertThat(SequenceFormat.format("B", ResetRule.NONE, "", 123, 6)).isEqualTo("B000123");
        assertThat(SequenceFormat.format("RC-", ResetRule.NONE, "", 42, 5)).isEqualTo("RC-00042");
        assertThat(SequenceFormat.format("SQ-", ResetRule.NONE, "", 7, 4)).isEqualTo("SQ-0007");
        assertThat(SequenceFormat.format("CP", ResetRule.NONE, "", 12, 4)).isEqualTo("CP0012");
    }

    @Test
    void periodFormats() {
        assertThat(SequenceFormat.format("QR-", ResetRule.YEARLY, "2026", 1, 3)).isEqualTo("QR-2026-001");
        assertThat(SequenceFormat.format("QT-", ResetRule.YEARLY, "2026", 15, 3)).isEqualTo("QT-2026-015");
        assertThat(SequenceFormat.format("PO-", ResetRule.YEARLY, "2026", 1, 4)).isEqualTo("PO-2026-0001");
        assertThat(SequenceFormat.format("INV-", ResetRule.MONTHLY, "2609", 1, 4)).isEqualTo("INV-2609-0001");
    }

    @Test
    void numberLongerThanPaddingIsNotCut() {
        assertThat(SequenceFormat.format("P", ResetRule.NONE, "", 100, 2)).isEqualTo("P100");
    }
}
