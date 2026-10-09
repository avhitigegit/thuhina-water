package lk.thuhina.water.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password.") @Size(max = 100) String currentPassword,
        @NotBlank(message = "Enter a new password.")
        @Size(min = 8, max = 100, message = "The new password must be at least 8 characters.") String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
