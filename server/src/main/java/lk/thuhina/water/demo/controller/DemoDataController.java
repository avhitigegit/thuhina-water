package lk.thuhina.water.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.demo.dto.DemoDataDtos.DemoDataRequest;
import lk.thuhina.water.demo.dto.DemoDataDtos.DemoDataResult;
import lk.thuhina.water.demo.dto.DemoDataDtos.DemoDataStatus;
import lk.thuhina.water.demo.service.DemoDataService;
import lk.thuhina.water.security.Permissions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-data generator (M12 S4). Only on local and UAT ({@code app.demo-data.enabled}); in production the endpoints do not
 * exist (404). Admin only.
 */
@Tag(name = "Demo data (local / UAT)")
@RestController
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
public class DemoDataController {

    private final DemoDataService service;

    public DemoDataController(DemoDataService service) {
        this.service = service;
    }

    @Operation(summary = "Suggested range and the last delivery bill (the client shows the generator only when this answers)")
    @GetMapping("/admin/demo-data")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public DemoDataStatus status() {
        return service.status();
    }

    @Operation(summary = "Make demo history for days without delivery bills (default two months up to yesterday)")
    @PostMapping("/admin/demo-data")
    @PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
    public DemoDataResult generate(@RequestBody(required = false) DemoDataRequest request) {
        return service.generate(request == null ? null : request.from(), request == null ? null : request.to());
    }
}
