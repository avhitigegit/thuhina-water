package lk.thuhina.water.admin.rules;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * User rules (M01, prototype {@code Ops.saveUser}) – pure functions, no database.
 * Messages are the prototype's wording; the Users pop-up shows them under the field.
 */
public final class UserRules {

    public static final String MSG_USERNAME = "Username: at least 3 lowercase letters, numbers, dots or underscores.";
    public static final String MSG_USERNAME_TAKEN = "Username already taken.";
    public static final String MSG_TEMP_PASSWORD = "Temporary password must be at least 8 characters.";

    public static final int USERNAME_MAX = 50;
    public static final int PASSWORD_MIN = 8;
    /** BCrypt uses only the first 72 bytes; longer passwords are refused rather than cut. */
    public static final int PASSWORD_MAX = 72;
    public static final int TEMP_PASSWORD_LENGTH = 10;

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._]{3,}$");

    /** Letters and digits that cannot be mixed up when read aloud or copied by hand (no 0/O, 1/l/I). */
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LETTERS + DIGITS;

    private UserRules() {
    }

    /** True when the (already lower-cased, trimmed) username follows the rule. */
    public static boolean isValidUsername(String username) {
        return username != null && username.length() <= USERNAME_MAX && USERNAME.matcher(username).matches();
    }

    public static boolean isValidTemporaryPassword(String password) {
        return password != null && password.length() >= PASSWORD_MIN && password.length() <= PASSWORD_MAX;
    }

    /**
     * Random temporary password for "Reset password": {@value #TEMP_PASSWORD_LENGTH} characters with at least
     * one letter and one digit, from an alphabet without look-alike characters.
     */
    public static String temporaryPassword(SecureRandom random) {
        char[] out = new char[TEMP_PASSWORD_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = ALL.charAt(random.nextInt(ALL.length()));
        }
        // Make sure both kinds are present, at random positions.
        int letterAt = random.nextInt(out.length);
        int digitAt = (letterAt + 1 + random.nextInt(out.length - 1)) % out.length;
        out[letterAt] = LETTERS.charAt(random.nextInt(LETTERS.length()));
        out[digitAt] = DIGITS.charAt(random.nextInt(DIGITS.length()));
        return new String(out);
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
