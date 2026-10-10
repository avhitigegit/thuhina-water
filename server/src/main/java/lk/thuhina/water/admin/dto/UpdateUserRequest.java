package lk.thuhina.water.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.admin.rules.UserRules;
import lk.thuhina.water.security.Role;

/**
 * Edit a user (name, username, role, phone). {@code version} is the value the screen loaded; when someone else
 * changed the user since, the save is refused with 409 CONFLICT. Leave it out to skip the check.
 */
public record UpdateUserRequest(
        @NotBlank(message = "Full name is required.") @Size(max = 100, message = "Full name: at most 100 characters.") String fullName,
        @NotBlank(message = UserRules.MSG_USERNAME) String username,
        @NotNull(message = "Choose a role.") Role role,
        @Size(max = 20, message = "Phone: at most 20 characters.") String phone,
        Long version) {
}
