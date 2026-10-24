package lk.thuhina.water.admin.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.admin.dto.CreateUserRequest;
import lk.thuhina.water.admin.dto.ResetPasswordResponse;
import lk.thuhina.water.admin.dto.UpdateUserRequest;
import lk.thuhina.water.admin.dto.UserResponse;
import lk.thuhina.water.admin.service.UserService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users tab of Administration (design 7.2 Admin). Admin only. */
@Tag(name = "Users")
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('" + Permissions.ADMIN_MANAGE + "')")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @Operation(summary = "All users, active first, then by name")
    @GetMapping
    public List<UserResponse> list() {
        return service.list();
    }

    @Operation(summary = "Create a user with a temporary password (changed at the first login)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Edit name, username, role and phone")
    @PutMapping("/{id}")
    public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Activate a user")
    @PostMapping("/{id}/activate")
    public UserResponse activate(@PathVariable long id) {
        return service.setActive(id, true);
    }

    @Operation(summary = "Deactivate a user (logs them out at once; not your own account)")
    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable long id) {
        return service.setActive(id, false);
    }

    @Operation(summary = "Issue a new temporary password – returned only in this response")
    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable long id) {
        return service.resetPassword(id);
    }
}
