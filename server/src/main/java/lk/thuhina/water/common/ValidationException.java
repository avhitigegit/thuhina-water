package lk.thuhina.water.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 400 VALIDATION raised by a service, with the same {@code details.fields} shape as Bean Validation errors. */
public class ValidationException extends BusinessException {

    public ValidationException(Map<String, Object> fields) {
        super(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION, GlobalExceptionHandler.MSG_VALIDATION, Map.of("fields", fields));
    }

    public ValidationException(String field, String message) {
        this(Map.of(field, message));
    }
}
