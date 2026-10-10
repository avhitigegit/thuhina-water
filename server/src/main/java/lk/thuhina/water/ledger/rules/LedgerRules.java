package lk.thuhina.water.ledger.rules;

import java.util.List;

/** BR-12: no stock or bottle balance may go below zero – the check and its message (design 5.1). Pure functions. */
public final class LedgerRules {

    public static final String STOCK_NEGATIVE = "STOCK_NEGATIVE";
    public static final String SUFFIX = "Record a stock adjustment first.";

    /** One balance a posting changes: e.g. "Filled in store (20L)", available 5, delta −8. */
    public record Line(String what, int available, int delta) {

        public boolean goesNegative() {
            return available + delta < 0;
        }
    }

    private LedgerRules() {
    }

    /** "Filled in store (20L) cannot go below zero – available 5, needed 8." for every line that would go negative. */
    public static List<String> problems(List<Line> lines) {
        return lines.stream()
                .filter(Line::goesNegative)
                .map(l -> l.what() + " cannot go below zero – available " + l.available() + ", needed " + (-l.delta()) + ".")
                .toList();
    }

    /** The full message: the problems, then "Record a stock adjustment first." */
    public static String message(List<String> problems) {
        return String.join(" ", problems) + " " + SUFFIX;
    }

    /** Short bottle name used in messages: litres "20.00" → "20L". */
    public static String bottleLabel(java.math.BigDecimal litres) {
        return litres.stripTrailingZeros().toPlainString() + "L";
    }
}
