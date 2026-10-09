package lk.thuhina.water.security;

import java.util.Set;

/** The logged-in user, put in the security context by {@code JwtAuthFilter} for each request. */
public record AuthUser(Long id, String username, String fullName, Role role, boolean mustChangePassword) {

    public Set<String> permissions() {
        return RolePermissions.of(role);
    }
}
