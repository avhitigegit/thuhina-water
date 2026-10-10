package lk.thuhina.water.masterdata.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lk.thuhina.water.common.AuditedEntity;
import org.springframework.data.domain.Persistable;

/**
 * A bottle type (FR-01), keyed by its code made from the size ({@code B20}). The size cannot change after creation.
 * Implements {@link Persistable} because the code is set before saving (so a new row is inserted, not merged).
 */
@Entity
@Table(name = "bottle_type")
public class BottleType extends AuditedEntity implements Persistable<String> {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal litres;

    @Column(nullable = false)
    private boolean active = true;

    @Transient
    private boolean isNew;

    protected BottleType() {
    }

    public BottleType(String code, String name, BigDecimal litres, boolean active) {
        this.code = code;
        this.name = name;
        this.litres = litres;
        this.active = active;
        this.isNew = true;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return code;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getLitres() {
        return litres;
    }

    public boolean isActive() {
        return active;
    }
}
