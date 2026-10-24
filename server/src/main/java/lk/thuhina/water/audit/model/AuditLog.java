package lk.thuhina.water.audit.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * One audit entry (FR-57). Insert-only: the entity is immutable, the repository has no update or
 * delete methods, and a database trigger rejects UPDATE / DELETE on {@code audit_log}.
 */
@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts;

    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    @Column(length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(nullable = false, length = 50)
    private String entity;

    @Column(length = 50)
    private String ref;

    @Column(columnDefinition = "text")
    private String details;

    @Column(length = 45)
    private String ip;

    protected AuditLog() {
    }

    public AuditLog(Instant ts, Long userId, String username, String role, AuditAction action,
                    String entity, String ref, String details, String ip) {
        this.ts = ts;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.action = action;
        this.entity = entity;
        this.ref = ref;
        this.details = details;
        this.ip = ip;
    }

    public Long getId() {
        return id;
    }

    public Instant getTs() {
        return ts;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public String getRef() {
        return ref;
    }

    public String getDetails() {
        return details;
    }

    public String getIp() {
        return ip;
    }
}
