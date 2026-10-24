package lk.thuhina.water.security.dto;

import java.util.List;

/**
 * Returned by login and {@code GET /auth/me}: who is logged in, what they may do, where to land and the
 * sidebar menu. The client hides buttons with {@code permissions} – the server still checks every call.
 */
public record MeResponse(
        UserInfo user,
        boolean mustChangePassword,
        String landingPage,
        List<String> permissions,
        List<MenuModule> menu) {

    public record UserInfo(Long id, String username, String fullName, String role, String roleName) {
    }

    /**
     * A sidebar module; {@code hidden} modules (switched off in settings) are left out of the sidebar.
     * {@code group} = the module has several pages, so the sidebar shows a heading with sub-items even when
     * the role sees only one of them (as the prototype does); otherwise a single link.
     */
    public record MenuModule(String module, String icon, boolean hidden, boolean group, List<MenuItem> items) {
    }

    /** A page; {@code view} = read-only for this user (the sidebar shows a "view" tag and write buttons are hidden). */
    public record MenuItem(String key, String title, String path, boolean view) {
    }
}
