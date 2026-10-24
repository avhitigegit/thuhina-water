package lk.thuhina.water.audit.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Choices for the Audit log filter drop-downs: users and record types that appear in the log, and all actions. */
public record AuditFilterOptions(List<UserOption> users, List<String> actions, List<String> entities) {

    public record UserOption(String username, @Schema(nullable = true) String fullName) {
    }
}
