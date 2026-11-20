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

    /** True when the logged-in user has {@code permission}. */
    public static boolean can(String permission) {
        return get().map(u -> u.permissions().contains(permission)).orElse(false);
    }

    /**
     * Customer phone numbers are shown only to users who may view customers (Admin, Accountant) – never to Delivery
     * Staff: their lists, searches, WhatsApp messages and printed sheets carry no phone number.
     */
    public static boolean seesCustomerPhones() {
        return can(Permissions.CUSTOMERS_VIEW);
    }

    public static String usernameOrSystem() {
        return get().map(AuthUser::username).orElse(SYSTEM);
    }
}
