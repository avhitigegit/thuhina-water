package lk.thuhina.water.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Money helpers: LKR, 2 decimals, HALF_UP (design 4.1, 14). */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    /** Rounds to 2 decimals; null counts as zero. */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String value) {
        return of(new BigDecimal(value));
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    /** unit × qty, rounded. */
    public static BigDecimal times(BigDecimal unit, int qty) {
        return of(of(unit).multiply(BigDecimal.valueOf(qty)));
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** Display format used on prints: {@code Rs. 1,350.00} (negative: {@code Rs. -1,350.00}). */
    public static String format(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        f.setRoundingMode(ROUNDING);
        return "Rs. " + f.format(of(value));
    }
}
