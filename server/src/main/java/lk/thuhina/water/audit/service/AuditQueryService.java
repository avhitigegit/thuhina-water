package lk.thuhina.water.audit.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.audit.dto.AuditEntryResponse;
import lk.thuhina.water.audit.dto.AuditFilterOptions;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.PageResponse;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit log search for the Administration page (M01, FR-57): from / to (business dates, Asia/Colombo), user,
 * action, record type and text search; newest first; paged. Read-only – audit rows are never changed.
 */
@Service
public class AuditQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    /** Filters; every one is optional. {@code to} includes the whole day. */
    public record Filter(LocalDate from, LocalDate to, String user, AuditAction action, String entity, String q) {
    }

    private final EntityManager em;
    private final AppUserRepository users;

    public AuditQueryService(EntityManager em, AppUserRepository users) {
        this.em = em;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> search(Filter filter, int page, int size) {
        if (filter.from() != null && filter.to() != null && filter.to().isBefore(filter.from())) {
            throw new ValidationException("to", "'To' must be on or after 'From'.");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AuditLog> countRoot = countQuery.from(AuditLog.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countRoot, filter));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<AuditLog> query = cb.createQuery(AuditLog.class);
        Root<AuditLog> root = query.from(AuditLog.class);
        query.select(root).where(predicates(cb, root, filter))
                .orderBy(cb.desc(root.get("ts")), cb.desc(root.get("id")));
        List<AuditLog> rows = em.createQuery(query)
                .setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize)
                .getResultList();

        Map<String, String> names = fullNames(rows.stream().map(AuditLog::getUsername).toList());
        List<AuditEntryResponse> items = rows.stream().map(a -> toResponse(a, names)).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    /** Users and record types found in the log (for the drop-downs), and every action. */
    @Transactional(readOnly = true)
    public AuditFilterOptions filterOptions() {
        List<String> usernames = em.createQuery(
                "select distinct a.username from AuditLog a where a.username is not null order by a.username", String.class)
                .getResultList();
        Map<String, String> names = fullNames(usernames);
        List<AuditFilterOptions.UserOption> userOptions = usernames.stream()
                .map(u -> new AuditFilterOptions.UserOption(u, names.get(u)))
                .toList();
        List<String> entities = em.createQuery(
                "select distinct a.entity from AuditLog a order by a.entity", String.class).getResultList();
        List<String> actions = Arrays.stream(AuditAction.values()).map(Enum::name).toList();
        return new AuditFilterOptions(userOptions, actions, entities);
    }

    private static Predicate[] predicates(CriteriaBuilder cb, Root<AuditLog> a, Filter f) {
        List<Predicate> p = new ArrayList<>();
        if (f.from() != null) {
            p.add(cb.greaterThanOrEqualTo(a.get("ts"), startOf(f.from())));
        }
        if (f.to() != null) {
            p.add(cb.lessThan(a.get("ts"), startOf(f.to().plusDays(1))));
        }
        if (hasText(f.user())) {
            p.add(cb.equal(a.get("username"), f.user().trim()));
        }
        if (f.action() != null) {
            p.add(cb.equal(a.get("action"), f.action()));
        }
        if (hasText(f.entity())) {
            p.add(cb.equal(a.get("entity"), f.entity().trim()));
        }
        if (hasText(f.q())) {
            String pattern = "%" + escapeLike(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
            Expression<String> text = cb.concat(cb.concat(cb.concat(cb.concat(cb.concat(
                    a.<String>get("entity"), " "),
                    cb.coalesce(a.<String>get("ref"), "")), " "),
                    cb.coalesce(a.<String>get("details"), "")), cb.concat(" ", cb.coalesce(a.<String>get("username"), "")));
            p.add(cb.like(cb.lower(text), pattern, '\\'));
        }
        return p.toArray(Predicate[]::new);
    }

    private Map<String, String> fullNames(List<String> usernames) {
        List<String> wanted = usernames.stream().filter(u -> u != null).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return users.findByUsernameIn(wanted).stream()
                .collect(Collectors.toMap(AppUser::getUsername, AppUser::getFullName, (x, y) -> x));
    }

    private static AuditEntryResponse toResponse(AuditLog a, Map<String, String> names) {
        return new AuditEntryResponse(a.getId(), a.getTs(), a.getUsername(),
                a.getUsername() == null ? null : names.get(a.getUsername()),
                a.getRole(), roleName(a.getRole()), a.getAction().name(), a.getEntity(), a.getRef(), a.getDetails());
    }

    private static String roleName(String role) {
        if (role == null) {
            return null;
        }
        return Arrays.stream(Role.values()).filter(r -> r.name().equals(role)).findFirst()
                .map(Role::label).orElse(role);
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(BusinessDates.ZONE).toInstant();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
