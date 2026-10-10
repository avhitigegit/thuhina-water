package lk.thuhina.water.masterdata.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import lk.thuhina.water.masterdata.model.PriceEntry;

/** Master data rules (M02, prototype {@code Ops.saveBottleType}, {@code Q.priceEntry}) – pure functions. */
public final class MasterDataRules {

    public static final BigDecimal MAX_LITRES = new BigDecimal("1000");

    /** Price status relative to a day (the Price history pop-up). */
    public enum PriceStatus { CURRENT, SCHEDULED, OLD }

    private MasterDataRules() {
    }

    /** Bottle code from the size: 20 → {@code B20}, 0.5 → {@code B0_5}, 18.9 → {@code B18_9}. */
    public static String bottleCode(BigDecimal litres) {
        return "B" + litres.stripTrailingZeros().toPlainString().replace('.', '_');
    }

    /** "20" for 20.00, "0.5" for 0.50 – for messages such as "A 19L bottle type already exists". */
    public static String litresText(BigDecimal litres) {
        return litres.stripTrailingZeros().toPlainString();
    }

    /** Size in litres: more than 0, at most 1000, at most 2 decimals. */
    public static boolean isValidLitres(BigDecimal litres) {
        return litres != null && litres.signum() > 0 && litres.compareTo(MAX_LITRES) <= 0
                && litres.stripTrailingZeros().scale() <= 2;
    }

    /**
     * The entry in force on {@code date}: the latest {@code effectiveFrom} on or before the date (FR-07).
     * {@code entries} must all be for the same price (kind, bottle type, customer type).
     */
    public static Optional<PriceEntry> inForce(Collection<PriceEntry> entries, LocalDate date) {
        return entries.stream()
                .filter(e -> !e.getEffectiveFrom().isAfter(date))
                .max(Comparator.comparing(PriceEntry::getEffectiveFrom));
    }

    /** The next scheduled entry after {@code date} (shown under the current price), if any. */
    public static Optional<PriceEntry> nextScheduled(Collection<PriceEntry> entries, LocalDate date) {
        return entries.stream()
                .filter(e -> e.getEffectiveFrom().isAfter(date))
                .min(Comparator.comparing(PriceEntry::getEffectiveFrom));
    }

    /** Current = in force today; Scheduled = starts after today; Old = replaced by a later entry. */
    public static PriceStatus status(PriceEntry entry, Collection<PriceEntry> sameKey, LocalDate today) {
        if (entry.getEffectiveFrom().isAfter(today)) {
            return PriceStatus.SCHEDULED;
        }
        return inForce(sameKey, today).filter(e -> e.getId().equals(entry.getId())).isPresent()
                ? PriceStatus.CURRENT : PriceStatus.OLD;
    }

    /**
     * Key of a price in the confirmed list (setting {@code prices.confirmed}): {@code DEPOSIT|B20} or
     * {@code WATER|B20|Household}.
     */
    public static String priceKey(PriceEntry.Kind kind, String bottleTypeCode, String customerTypeName) {
        return kind == PriceEntry.Kind.DEPOSIT ? "DEPOSIT|" + bottleTypeCode : "WATER|" + bottleTypeCode + "|" + customerTypeName;
    }
}
