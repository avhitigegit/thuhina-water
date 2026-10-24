package lk.thuhina.water.security.service;

import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lk.thuhina.water.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The login cookie: {@code HttpOnly; Secure; SameSite=Strict}, 12 hours (design 8.1).
 * JavaScript cannot read it, and the browser does not send it from other sites.
 * {@code Secure} is off only in the local profile, where the app runs on plain http://localhost.
 */
@Component
public class AuthCookies {

    private final String name;
    private final boolean secure;
    private final Duration ttl;

    public AuthCookies(AppProperties properties) {
        this.name = properties.cookie().name();
        this.secure = properties.cookie().secure();
        this.ttl = properties.jwt().ttl();
    }

    public String name() {
        return name;
    }

    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, ttl).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
