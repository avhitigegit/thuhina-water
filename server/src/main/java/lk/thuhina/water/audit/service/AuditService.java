package lk.thuhina.water.audit.service;

import java.time.Clock;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit entries (design 5.7). Called explicitly from services – not by AOP – inside the
 * caller's transaction, with readable details, e.g.
 * {@code audit.log(AuditAction.CREATE, "Customer", "C0041", "Registered W.M. Sunil Perera")}.
 */
@Service
public class AuditService {

    private static final int MAX_DETAILS = 4000;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Entry for the logged-in user ("system" when there is none, e.g. start-up). */
    @Transactional
    public AuditLog log(AuditAction action, String entity, String ref, String details) {
        AuthUser user = CurrentUser.get().orElse(null);
        if (user == null) {
            return write(null, CurrentUser.SYSTEM, null, action, entity, ref, details);
        }
        return write(user.id(), user.username(), user.role().name(), action, entity, ref, details);
    }

    /** Entry for a named user – used by login, before the user is in the security context. */
    @Transactional
    public AuditLog logFor(AuthUser user, AuditAction action, String entity, String ref, String details) {
        return write(user.id(), user.username(), user.role().name(), action, entity, ref, details);
    }

    private AuditLog write(Long userId, String username, String role, AuditAction action,
                           String entity, String ref, String details) {
        String text = details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details;
        return repository.save(new AuditLog(Instant.now(clock), userId, username, role, action, entity, ref, text, clientIp()));
    }

    /** Client address; behind Nginx the forwarded headers are applied by {@code server.forward-headers-strategy}. */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
