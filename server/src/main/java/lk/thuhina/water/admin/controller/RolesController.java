package lk.thuhina.water.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.security.Permissions;
import lk.thuhina.water.security.dto.RolesMatrixResponse;
import lk.thuhina.water.security.service.MenuService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles &amp; access tab: read-only – roles are fixed in code (design 8.2). Admin only. */
@Tag(name = "Roles")
@RestController
@RequestMapping("/roles")
@PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
public class RolesController {

    private final MenuService menu;

    public RolesController(MenuService menu) {
        this.menu = menu;
    }

    @Operation(summary = "Menu pages and special rights × roles → FULL / VIEW / NONE")
    @GetMapping("/matrix")
    public RolesMatrixResponse matrix() {
        return menu.rolesMatrix();
    }
}
