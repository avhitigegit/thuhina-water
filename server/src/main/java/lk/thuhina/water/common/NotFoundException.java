package lk.thuhina.water.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 404 – the requested record does not exist. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String what, Object ref) {
        super(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, what + " not found.", Map.of("ref", String.valueOf(ref)));
    }
}
