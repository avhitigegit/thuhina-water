package lk.thuhina.water.masterdata.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One dated standard price (FR-04, FR-07): a WATER price for a bottle type and customer type, or a DEPOSIT for a
 * bottle type. Insert-only – a price change is a new entry with its effective date (a database trigger blocks
 * UPDATE / DELETE).
 */
@Entity
@Immutable
@Table(name = "price_entry")
@EntityListeners(AuditingEntityListener.class)
public class PriceEntry {

    public enum Kind { WATER, DEPOSIT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(name = "customer_type_id")
    private Long customerTypeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(nullable = false, length = 200)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected PriceEntry() {
    }

    public PriceEntry(Kind kind, String bottleTypeCode, Long customerTypeId, BigDecimal price, LocalDate effectiveFrom,
                      String reason) {
        this.kind = kind;
        this.bottleTypeCode = bottleTypeCode;
        this.customerTypeId = customerTypeId;
        this.price = price;
        this.effectiveFrom = effectiveFrom;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public Long getCustomerTypeId() {
        return customerTypeId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
