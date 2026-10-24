package lk.thuhina.water.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lk.thuhina.water.masterdata.model.PriceEntry;
import lk.thuhina.water.masterdata.model.PriceEntry.Kind;
import lk.thuhina.water.masterdata.rules.MasterDataRules;
import lk.thuhina.water.masterdata.rules.MasterDataRules.PriceStatus;
import org.junit.jupiter.api.Test;

/** Bottle code from size and the price-at-a-date rule (FR-07). */
class MasterDataRulesTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
    private static final LocalDate NOV = LocalDate.of(2026, 11, 1);

    private static PriceEntry entry(long id, int price, LocalDate from) {
        PriceEntry e = new PriceEntry(Kind.WATER, "B10", 4L, BigDecimal.valueOf(price), from, "test");
        try {
            Field f = PriceEntry.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return e;
    }

    private final PriceEntry jan = entry(1, 160, JAN);
    private final PriceEntry jul = entry(2, 175, JUL);
    private final PriceEntry nov = entry(3, 180, NOV);
    private final List<PriceEntry> all = List.of(nov, jan, jul);

    @Test
    void codeIsMadeFromTheSize() {
        assertThat(MasterDataRules.bottleCode(new BigDecimal("19"))).isEqualTo("B19");
        assertThat(MasterDataRules.bottleCode(new BigDecimal("20.00"))).isEqualTo("B20");
        assertThat(MasterDataRules.bottleCode(new BigDecimal("0.5"))).isEqualTo("B0_5");
        assertThat(MasterDataRules.bottleCode(new BigDecimal("18.90"))).isEqualTo("B18_9");
        assertThat(MasterDataRules.bottleCode(new BigDecimal("100"))).isEqualTo("B100");
        assertThat(MasterDataRules.litresText(new BigDecimal("19.00"))).isEqualTo("19");
    }

    @Test
    void sizeMustBePositiveUpTo1000WithTwoDecimals() {
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("0.5"))).isTrue();
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("1000"))).isTrue();
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("18.75"))).isTrue();
        assertThat(MasterDataRules.isValidLitres(BigDecimal.ZERO)).isFalse();
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("-5"))).isFalse();
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("1000.01"))).isFalse();
        assertThat(MasterDataRules.isValidLitres(new BigDecimal("1.255"))).isFalse();
        assertThat(MasterDataRules.isValidLitres(null)).isFalse();
    }

    @Test
    void priceInForceAtTheDateBoundaries() {
        assertThat(MasterDataRules.inForce(all, JAN.minusDays(1))).isEmpty();
        assertThat(MasterDataRules.inForce(all, JAN)).contains(jan);
        assertThat(MasterDataRules.inForce(all, JUL.minusDays(1))).contains(jan);   // day before
        assertThat(MasterDataRules.inForce(all, JUL)).contains(jul);                 // on the day
        assertThat(MasterDataRules.inForce(all, JUL.plusDays(1))).contains(jul);     // day after
        assertThat(MasterDataRules.inForce(all, NOV.minusDays(1))).contains(jul);
        assertThat(MasterDataRules.inForce(all, NOV)).contains(nov);
    }

    @Test
    void nextScheduledAndStatus() {
        LocalDate today = LocalDate.of(2026, 10, 2);
        assertThat(MasterDataRules.nextScheduled(all, today)).contains(nov);
        assertThat(MasterDataRules.nextScheduled(all, NOV)).isEmpty();

        assertThat(MasterDataRules.status(jan, all, today)).isEqualTo(PriceStatus.OLD);
        assertThat(MasterDataRules.status(jul, all, today)).isEqualTo(PriceStatus.CURRENT);
        assertThat(MasterDataRules.status(nov, all, today)).isEqualTo(PriceStatus.SCHEDULED);
        // On 01/11/2026 the scheduled one becomes current.
        assertThat(MasterDataRules.status(nov, all, NOV)).isEqualTo(PriceStatus.CURRENT);
        assertThat(MasterDataRules.status(jul, all, NOV)).isEqualTo(PriceStatus.OLD);
    }

    @Test
    void confirmedPriceKeys() {
        assertThat(MasterDataRules.priceKey(Kind.DEPOSIT, "B20", null)).isEqualTo("DEPOSIT|B20");
        assertThat(MasterDataRules.priceKey(Kind.WATER, "B20", "Household")).isEqualTo("WATER|B20|Household");
    }
}
