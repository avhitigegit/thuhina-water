package lk.thuhina.water.common;

import java.util.regex.Pattern;

/** Small text helpers for request values. */
public final class Texts {

    public static final String MSG_EMAIL = "Enter a valid email address.";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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

    /** A simple email check (something@something.something); null / empty counts as valid (optional field). */
    public static boolean isEmailOrEmpty(String value) {
        return value == null || value.isBlank() || EMAIL.matcher(value.trim()).matches();
    }
}
