package lk.thuhina.water.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** The fixed payment terms list for suppliers and factories (prototype {@code TERMS}): cash on delivery or 7 – 60 days. */
public final class PaymentTerms {

    public static final String MSG_CHOOSE = "Choose the payment terms from the list.";

    private static final Map<Integer, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(0, "Cash on delivery");
        LABELS.put(7, "7 days");
        LABELS.put(14, "14 days");
        LABELS.put(30, "30 days");
        LABELS.put(45, "45 days");
        LABELS.put(60, "60 days");
    }

    private PaymentTerms() {
    }

    public static boolean isValid(Integer days) {
        return days != null && LABELS.containsKey(days);
    }

    /** "Cash on delivery", "30 days" … */
    public static String label(int days) {
        return LABELS.getOrDefault(days, days + " days");
    }
}
