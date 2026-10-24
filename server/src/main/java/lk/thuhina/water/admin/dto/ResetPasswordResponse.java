package lk.thuhina.water.admin.dto;

/** Result of "Reset password": the temporary password is returned this once and never stored in clear text. */
public record ResetPasswordResponse(UserResponse user, String temporaryPassword) {

    @Override
    public String toString() {
        return "ResetPasswordResponse[user=" + user.username() + "]";
    }
}
