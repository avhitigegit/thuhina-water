package lk.thuhina.water.common;

import java.util.regex.Pattern;

/** Small text helpers for request values. */
public final class Texts {

    public static final String MSG_EMAIL = "Enter a valid email address.";

    public static final String MSG_PHONE = "Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471.";

    private static final Pattern PHONE = Pattern.compile("^0\\d{2} ?\\d{3} ?\\d{4}$");

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

    /** Sri Lankan phone number (077 123 4567, 0112854471); null / empty counts as valid (optional field). */
    public static boolean isPhoneOrEmpty(String value) {
        return value == null || value.isBlank() || PHONE.matcher(value.trim()).matches();
    }
}
