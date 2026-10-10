package lk.thuhina.water.admin.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lk.thuhina.water.admin.dto.CreateUserRequest;
import lk.thuhina.water.admin.dto.ResetPasswordResponse;
import lk.thuhina.water.admin.dto.UpdateUserRequest;
import lk.thuhina.water.admin.dto.UserResponse;
import lk.thuhina.water.admin.mapper.UserMapper;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.admin.rules.UserRules;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.repository.LoginAttemptRepository;
import lk.thuhina.water.common.Versions;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users (M01, FR-56): the Admin creates users with a temporary password (no public signup), edits them,
 * deactivates / activates them and resets passwords. Every change is audited (FR-57).
 */
@Service
public class UserService {

    public static final String SELF_DEACTIVATE = "SELF_DEACTIVATE";
    public static final String SELF_ROLE_CHANGE = "SELF_ROLE_CHANGE";
    public static final String SELF_RESET = "SELF_RESET";

    static final String ENTITY = "User";

    private final AppUserRepository users;
    private final LoginAttemptRepository loginAttempts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public UserService(AppUserRepository users, LoginAttemptRepository loginAttempts, PasswordEncoder encoder,
                       AuditService audit) {
        this.users = users;
        this.loginAttempts = loginAttempts;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByActiveDescFullNameAsc().stream().map(UserMapper::toResponse).toList();
    }

    /** New user with a temporary password; the user must change it at the first login. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = AppUser.normalizeUsername(request.username());
        checkUsername(username, null);
        if (!UserRules.isValidTemporaryPassword(request.temporaryPassword())) {
            throw new ValidationException("temporaryPassword", request.temporaryPassword().length() > UserRules.PASSWORD_MAX
                    ? "Temporary password: at most " + UserRules.PASSWORD_MAX + " characters."
                    : UserRules.MSG_TEMP_PASSWORD);
        }
        AppUser user = users.save(new AppUser(username, request.fullName().trim(), UserRules.blankToNull(request.phone()),
                request.role(), encoder.encode(request.temporaryPassword()), true));
        audit.log(AuditAction.CREATE, ENTITY, user.getUsername(), user.getFullName() + " – " + user.getRole().label());
        return UserMapper.toResponse(user);
    }

    /** Edit name, username, role and phone. A role change is audited as "role Accountant → Admin". */
    @Transactional
    public UserResponse update(long id, UpdateUserRequest request) {
        AppUser user = find(id);
        Versions.check(request.version(), user.getVersion(), "User " + id);
        String username = AppUser.normalizeUsername(request.username());
        checkUsername(username, id);
        if (isSelf(user) && request.role() != user.getRole()) {
            throw new BusinessException(SELF_ROLE_CHANGE, "You cannot change your own role.");
        }

        String fullName = request.fullName().trim();
        String phone = UserRules.blankToNull(request.phone());
        List<String> changes = new ArrayList<>();
        if (request.role() != user.getRole()) {
            changes.add("role " + user.getRole().label() + " → " + request.role().label());
        }
        if (!username.equals(user.getUsername())) {
            changes.add("username " + user.getUsername() + " → " + username);
        }
        if (!fullName.equals(user.getFullName())) {
            changes.add("name " + user.getFullName() + " → " + fullName);
        }
        if (!Objects.equals(phone, user.getPhone())) {
            changes.add("phone changed");
        }
        if (changes.isEmpty()) {
            return UserMapper.toResponse(user);
        }
        user.updateDetails(fullName, username, phone, request.role());
        users.saveAndFlush(user);
        audit.log(AuditAction.UPDATE, ENTITY, user.getUsername(), String.join("; ", changes));
        return UserMapper.toResponse(user);
    }

    /** Deactivate (logs the user out at once) or activate. Nobody can deactivate their own account. */
    @Transactional
    public UserResponse setActive(long id, boolean active) {
        AppUser user = find(id);
        if (!active && isSelf(user)) {
            throw new BusinessException(SELF_DEACTIVATE, "You cannot deactivate your own account.");
        }
        if (user.isActive() == active) {
            return UserMapper.toResponse(user);
        }
        user.setActive(active);
        users.saveAndFlush(user);
        audit.log(AuditAction.UPDATE, ENTITY, user.getUsername(), active ? "Activated" : "Deactivated");
        return UserMapper.toResponse(user);
    }

    /**
     * New random temporary password, shown once to the Admin. The user is logged out, must change it at the next
     * login, and any login lock on the username is cleared. Admins change their own password from the top bar.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(long id) {
        AppUser user = find(id);
        if (isSelf(user)) {
            throw new BusinessException(SELF_RESET, "Use Change password in the top bar for your own account.");
        }
        String temporary = UserRules.temporaryPassword(random);
        user.resetPassword(encoder.encode(temporary));
        users.saveAndFlush(user);
        loginAttempts.deleteById(user.getUsername());
        audit.log(AuditAction.UPDATE, ENTITY, user.getUsername(), "Password reset – temporary password issued");
        return new ResetPasswordResponse(UserMapper.toResponse(user), temporary);
    }

    private void checkUsername(String username, Long id) {
        if (!UserRules.isValidUsername(username)) {
            throw new ValidationException("username", UserRules.MSG_USERNAME);
        }
        boolean taken = id == null ? users.existsByUsername(username) : users.existsByUsernameAndIdNot(username, id);
        if (taken) {
            throw new ValidationException("username", UserRules.MSG_USERNAME_TAKEN);
        }
    }

    private AppUser find(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private static boolean isSelf(AppUser user) {
        return CurrentUser.get().map(u -> u.id().equals(user.getId())).orElse(false);
    }
}
