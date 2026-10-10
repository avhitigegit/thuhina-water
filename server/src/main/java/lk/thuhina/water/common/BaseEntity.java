package lk.thuhina.water.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base class for editable tables with an identity id (design 4.1); who/when and the version column come from
 * {@link AuditedEntity}.
 */
@MappedSuperclass
public abstract class BaseEntity extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }
}
