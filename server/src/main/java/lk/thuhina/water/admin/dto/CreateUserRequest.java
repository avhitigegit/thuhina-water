package lk.thuhina.water.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.admin.rules.UserRules;
import lk.thuhina.water.security.Role;

/** New user (M01). The username rule and "already taken" are checked by {@code UserService}. */
public record CreateUserRequest(
        @NotBlank(message = "Full name is required.") @Size(max = 100, message = "Full name: at most 100 characters.") String fullName,
        @NotBlank(message = UserRules.MSG_USERNAME) String username,
        @NotNull(message = "Choose a role.") Role role,
        @Size(max = 20, message = "Phone: at most 20 characters.") String phone,
        @NotBlank(message = UserRules.MSG_TEMP_PASSWORD) String temporaryPassword) {

    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", role=" + role + "]";
    }
}
