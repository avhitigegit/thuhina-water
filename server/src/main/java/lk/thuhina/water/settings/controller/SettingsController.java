package lk.thuhina.water.settings.controller;

import java.util.concurrent.TimeUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lk.thuhina.water.security.Permissions;
import lk.thuhina.water.settings.service.CompanyLogoService;
import lk.thuhina.water.settings.service.SettingsGroup;
import lk.thuhina.water.settings.service.SettingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Settings groups (company, business, features) and the company logo. The company group also returns
 * {@code logoUrl} – the address of the logo image, or null when there is none.
 */
@Tag(name = "Settings")
@RestController
@RequestMapping("/settings")
public class SettingsController {

    /** Relative to {@code /settings}. */
    private static final String LOGO_PATH = "/company/logo";

    private final SettingsService settings;
    private final CompanyLogoService logo;

    public SettingsController(SettingsService settings, CompanyLogoService logo) {
        this.settings = settings;
        this.logo = logo;
    }

    @Operation(summary = "Read a settings group: company, business or features")
    @GetMapping("/{group}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode get(@PathVariable String group, HttpServletRequest request) {
        return withLogoUrl(group, settings.get(group), request);
    }

    @Operation(summary = "Change some keys of a settings group; returns the whole group. The company logo has its own upload")
    @PutMapping("/{group}")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode update(@PathVariable String group, @RequestBody JsonNode changes, HttpServletRequest request) {
        return withLogoUrl(group, settings.update(group, changes), request);
    }

    @Operation(summary = "Upload the company logo (PNG or JPG, at most 1 MB); returns the company settings")
    @PostMapping(value = LOGO_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode uploadLogo(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        return withLogoUrl(SettingsGroup.COMPANY.key(), logo.upload(file), request);
    }

    @Operation(summary = "Remove the company logo; returns the company settings")
    @DeleteMapping(LOGO_PATH)
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public JsonNode removeLogo(HttpServletRequest request) {
        return withLogoUrl(SettingsGroup.COMPANY.key(), logo.remove(), request);
    }

    @Operation(summary = "The company logo image (any logged-in user – it appears on printed documents)")
    @GetMapping(LOGO_PATH)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> logoImage() {
        return logo.current()
                .map(l -> ResponseEntity.ok()
                        .contentType(l.mediaType())
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                        .body(l.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Adds {@code logoUrl} to the company group; the key in the URL changes with every upload, so caches refresh. */
    private ObjectNode withLogoUrl(String group, ObjectNode node, HttpServletRequest request) {
        if (!SettingsGroup.COMPANY.key().equals(group)) {
            return node;
        }
        ObjectNode copy = node.deepCopy();
        JsonNode key = node.get("logo");
        if (key != null && key.isString() && !key.asString().isBlank()) {
            String version = key.asString().replaceAll("^.*logo-|\\..*$", "");
            copy.put("logoUrl", request.getContextPath() + "/settings" + LOGO_PATH + "?v=" + version);
        } else {
            copy.putNull("logoUrl");
        }
        return copy;
    }
}
