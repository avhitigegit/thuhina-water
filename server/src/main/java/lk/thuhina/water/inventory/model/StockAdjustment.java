package lk.thuhina.water.inventory.model;

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
 * A stock adjustment (FR-30): a physical count (bucket set to the counted quantity) or lost bottles (written off).
 * {@code bucket} is EMPTY / FILLED / FACTORY for bottles and STOCK for products. The change is a ledger posting with
 * source ADJUSTMENT.
 */
@Entity
@Immutable
@Table(name = "stock_adjustment")
@EntityListeners(AuditingEntityListener.class)
public class StockAdjustment {

    public enum Mode { COUNT, LOST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adj_no", nullable = false, unique = true, length = 20)
    private String adjNo;

    @Column(name = "adj_date", nullable = false)
    private LocalDate adjDate;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    @Column(nullable = false, length = 12)
    private String bucket;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Mode mode;

    @Column(name = "system_qty", nullable = false)
    private int systemQty;

    @Column(name = "counted_qty")
    private Integer countedQty;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false, length = 200)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected StockAdjustment() {
    }

    public StockAdjustment(String adjNo, LocalDate adjDate, String itemCode, String bucket, Mode mode, int systemQty,
                           Integer countedQty, int delta, String reason) {
        this.adjNo = adjNo;
        this.adjDate = adjDate;
        this.itemCode = itemCode;
        this.bucket = bucket;
        this.mode = mode;
        this.systemQty = systemQty;
        this.countedQty = countedQty;
        this.delta = delta;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getAdjNo() {
        return adjNo;
    }

    public LocalDate getAdjDate() {
        return adjDate;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getBucket() {
        return bucket;
    }

    public Mode getMode() {
        return mode;
    }

    public int getSystemQty() {
        return systemQty;
    }

    public Integer getCountedQty() {
        return countedQty;
    }

    public int getDelta() {
        return delta;
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
