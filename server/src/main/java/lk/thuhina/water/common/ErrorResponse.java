package lk.thuhina.water.common;

import java.util.Map;

/** Body of every error response: {@code { "code", "message", "details" }}. */
public record ErrorResponse(String code, String message, Map<String, Object> details) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, Map.of());
    }
}
