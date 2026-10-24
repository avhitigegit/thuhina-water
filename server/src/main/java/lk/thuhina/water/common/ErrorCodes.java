package lk.thuhina.water.common;

/**
 * Error codes shared by every module (design 5.8). Modules add their own business codes
 * (STOCK_NEGATIVE, CREDIT_LIMIT, DUPLICATE_PAPER_BILL …) next to their rules.
 */
public final class ErrorCodes {

    public static final String VALIDATION = "VALIDATION";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String INVALID_STATE = "INVALID_STATE";
    public static final String SERVER_ERROR = "SERVER_ERROR";

    private ErrorCodes() {
    }
}
