package lk.thuhina.water.admin.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** A row of the Users tab. {@code version} is sent back with an edit to detect changes by someone else. */
public record UserResponse(
        long id,
        String username,
        String fullName,
        @Schema(nullable = true) String phone,
        String role,
        String roleName,
        boolean active,
        boolean mustChangePassword,
        @Schema(nullable = true) Instant lastLoginAt,
        long version) {
}
