package lk.thuhina.water.settings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.security.Permissions;
import lk.thuhina.water.settings.service.SettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@Tag(name = "Settings")
@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @Operation(summary = "Read a settings group: company, business or features")
    @GetMapping("/{group}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode get(@PathVariable String group) {
        return settings.get(group);
    }

    @Operation(summary = "Change some keys of a settings group; returns the whole group")
    @PutMapping("/{group}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode update(@PathVariable String group, @RequestBody JsonNode changes) {
        return settings.update(group, changes);
    }
}
