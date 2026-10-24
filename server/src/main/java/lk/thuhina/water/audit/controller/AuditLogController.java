package lk.thuhina.water.audit.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.audit.dto.AuditEntryResponse;
import lk.thuhina.water.audit.dto.AuditFilterOptions;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditQueryService;
import lk.thuhina.water.common.PageResponse;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Audit log tab of Administration (FR-57). Read-only; Admin only. */
@Tag(name = "Audit log")
@RestController
@RequestMapping("/audit-log")
@PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
public class AuditLogController {

    private final AuditQueryService service;

    public AuditLogController(AuditQueryService service) {
        this.service = service;
    }

    @Operation(summary = "Search the audit log, newest first. Dates are business dates (ISO); 'to' includes the whole day")
    @GetMapping
    public PageResponse<AuditEntryResponse> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(new AuditQueryService.Filter(from, to, user, action, entity, q), page, size);
    }

    @Operation(summary = "Users, actions and record types for the filter drop-downs")
    @GetMapping("/filters")
    public AuditFilterOptions filters() {
        return service.filterOptions();
    }
}
