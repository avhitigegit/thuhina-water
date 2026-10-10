package lk.thuhina.water.audit.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** One row of the Audit log tab: when, user (+ name and role), action, record (+ ref) and details. */
public record AuditEntryResponse(
        long id,
        Instant ts,
        @Schema(nullable = true) String username,
        @Schema(nullable = true, description = "The user's full name, when the user still exists") String fullName,
        @Schema(nullable = true) String role,
        @Schema(nullable = true) String roleName,
        String action,
        String entity,
        @Schema(nullable = true) String ref,
        @Schema(nullable = true) String details) {
}
