package lk.thuhina.water.sales.rules;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reason a bill cannot be saved, in the prototype's words. {@code field} names the input it belongs to (or null for
 * a rule about the customer's balances); {@code code} is the error code when saving.
 */
public record Problem(@Schema(nullable = true) String field, String code, String message) {

    /** Error code of rule failures without their own code. */
    public static final String SALE_RULE = "SALE_RULE";
    /** Fewer empties than filled bottles on an exchange without a note (FR-38). */
    public static final String NOTE_REQUIRED = "NOTE_REQUIRED";

    public static Problem field(String field, String message) {
        return new Problem(field, SALE_RULE, message);
    }

    public static Problem rule(String message) {
        return new Problem(null, SALE_RULE, message);
    }
}
