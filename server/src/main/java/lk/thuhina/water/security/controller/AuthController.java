package lk.thuhina.water.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.dto.ChangePasswordRequest;
import lk.thuhina.water.security.dto.LoginRequest;
import lk.thuhina.water.security.dto.MeResponse;
import lk.thuhina.water.security.service.AuthCookies;
import lk.thuhina.water.security.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login / logout / me / change password (design 7.2 Auth). Login and logout are open; the others need a login. */
@Tag(name = "Auth")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthCookies cookies;

    public AuthController(AuthService auth, AuthCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @Operation(summary = "Log in; sets the httpOnly login cookie and returns the user, permissions and menu")
    @PostMapping("/login")
    public MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.login(request);
        cookies.write(response, result.token());
        return result.me();
    }

    @Operation(summary = "Log out; clears the login cookie")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        auth.logout(CurrentUser.get().orElse(null));
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "The logged-in user, role, permissions, landing page and sidebar menu")
    @GetMapping("/me")
    public MeResponse me() {
        return auth.me(CurrentUser.require());
    }

    @Operation(summary = "Change your own password (at least 8 characters, not the same as the old one)")
    @PostMapping("/change-password")
    public MeResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.changePassword(CurrentUser.require(), request);
        cookies.write(response, result.token());
        return result.me();
    }
}
