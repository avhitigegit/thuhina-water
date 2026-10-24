package lk.thuhina.water.partners.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A factory's filling charge per bottle of one type, from a date (BR-11). Insert-only – a change is a new row
 * (database trigger blocks UPDATE / DELETE). When two rows start on the same day, the later one (higher id) counts.
 */
@Entity
@Immutable
@Table(name = "factory_charge")
@EntityListeners(AuditingEntityListener.class)
public class FactoryCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal charge;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected FactoryCharge() {
    }

    public FactoryCharge(Long factoryId, String bottleTypeCode, BigDecimal charge, LocalDate effectiveFrom) {
        this.factoryId = factoryId;
        this.bottleTypeCode = bottleTypeCode;
        this.charge = charge;
        this.effectiveFrom = effectiveFrom;
    }

    public Long getId() {
        return id;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public BigDecimal getCharge() {
        return charge;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
