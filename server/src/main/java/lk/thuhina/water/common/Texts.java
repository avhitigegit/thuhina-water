package lk.thuhina.water.common;

/** Small text helpers for request values. */
public final class Texts {

    private Texts() {
    }

    /** Optional text: trimmed, empty → null. */
    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
