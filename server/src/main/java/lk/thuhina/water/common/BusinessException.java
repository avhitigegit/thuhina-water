package lk.thuhina.water.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * A business rule failure (design 5.8). Returned to the client as
 * {@code { "code", "message", "details" }}, by default with status 422.
 * Messages use the prototype's plain-English wording because the UI shows them as they are.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final transient Map<String, Object> details;
    private final HttpStatus status;

    public BusinessException(String code, String message) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, code, message, Map.of());
    }

    public BusinessException(String code, String message, Map<String, Object> details) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, code, message, details);
    }

    protected BusinessException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
