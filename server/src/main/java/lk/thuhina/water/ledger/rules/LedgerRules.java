package lk.thuhina.water.ledger.rules;

import java.util.List;

/** BR-12: no stock or bottle balance may go below zero – the check and its message (design 5.1). Pure functions. */
public final class LedgerRules {

    public static final String STOCK_NEGATIVE = "STOCK_NEGATIVE";
    public static final String SUFFIX = "Record a stock adjustment first.";

    /**
     * One balance a posting changes: e.g. "Filled in store (20L)", available 5, delta −8. Customer balances
     * ({@code customer} = true) read "Bottles held (20L) for W.M. Sunil Perera cannot go below zero – currently 2."
     */
    public record Line(String what, int available, int delta, boolean customer) {

        public Line(String what, int available, int delta) {
            this(what, available, delta, false);
        }

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
                .map(l -> l.customer()
                        ? l.what() + " cannot go below zero – currently " + l.available() + "."
                        : l.what() + " cannot go below zero – available " + l.available() + ", needed " + (-l.delta()) + ".")
                .toList();
    }

    /** The full message: the problems, then "Record a stock adjustment first." when stock is short. */
    public static String message(List<String> problems) {
        return message(problems, true);
    }

    public static String message(List<String> problems, boolean stockShort) {
        return String.join(" ", problems) + (stockShort ? " " + SUFFIX : "");
    }

    /** Short bottle name used in messages: litres "20.00" → "20L". */
    public static String bottleLabel(java.math.BigDecimal litres) {
        return litres.stripTrailingZeros().toPlainString() + "L";
    }
}
