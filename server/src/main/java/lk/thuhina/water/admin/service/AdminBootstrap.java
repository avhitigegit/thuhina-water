package lk.thuhina.water.admin.service;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.config.AppProperties;
import lk.thuhina.water.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * First run on an empty database (production): creates the first Admin from ADMIN_USERNAME and
 * ADMIN_TEMP_PASSWORD. The password is temporary – the Admin must change it at the first login.
 * Does nothing once any user exists (local / UAT get their users from the seed data).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD = 8;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties.Bootstrap props;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder, AuditService audit, AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.props = properties.bootstrap();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String username = AppUser.normalizeUsername(props.adminUsername());
        String password = props.adminTempPassword();
        if (username == null || username.isEmpty() || password == null || password.length() < MIN_PASSWORD) {
            log.warn("No users exist. Set ADMIN_USERNAME and ADMIN_TEMP_PASSWORD (at least {} characters) "
                    + "and restart to create the first Admin.", MIN_PASSWORD);
            return;
        }
        AppUser admin = users.save(new AppUser(username, props.adminFullName(), null, Role.ADMIN, encoder.encode(password), true));
        audit.log(AuditAction.CREATE, "User", admin.getUsername(), "First Admin created at start-up (must change password)");
        log.info("Created the first Admin user '{}' (must change the password at first login)", admin.getUsername());
    }
}
