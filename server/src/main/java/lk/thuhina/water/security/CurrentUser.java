package lk.thuhina.water.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Access to the logged-in user from services (audit, created_by, approvals). */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    public static Optional<AuthUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The logged-in user; use only where the endpoint requires login. */
    public static AuthUser require() {
        return get().orElseThrow(() -> new IllegalStateException("No logged-in user"));
    }

    public static String usernameOrSystem() {
        return get().map(AuthUser::username).orElse(SYSTEM);
    }
}
