package lk.thuhina.water.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Enter your username.") @Size(max = 50) String username,
        @NotBlank(message = "Enter your password.") @Size(max = 100) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
