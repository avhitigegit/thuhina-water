package lk.thuhina.water.security.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Roles &amp; access tab (design 8.2): one row per menu page plus the special rights, one cell per role
 * in the order of {@code roles}. Built from the same permission map the server uses for security.
 */
public record RolesMatrixResponse(List<RoleColumn> roles, List<Row> rows) {

    public record RoleColumn(String role, String roleName) {
    }

    /** {@code area} = "Module › Page" (or the page title when the module has one page). */
    public record Row(String area, List<Cell> access) {
    }

    /** {@code level} FULL / VIEW / NONE; {@code note} e.g. "View + print" or the reports a role sees. */
    public record Cell(String level, @Schema(nullable = true) String note) {
    }
}
