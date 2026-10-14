package lk.thuhina.water.production.model;

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

/** Filled bottles (and rejects) back from the factory for one batch (FR-24); cost = filled × the batch charge (BR-11). */
@Entity
@Immutable
@Table(name = "factory_return")
@EntityListeners(AuditingEntityListener.class)
public class FactoryReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, unique = true, length = 20)
    private String returnNo;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "filled_qty", nullable = false)
    private int filledQty;

    @Column(name = "rejected_qty", nullable = false)
    private int rejectedQty;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(nullable = false)
    private BigDecimal cost;

    @Column(length = 200)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected FactoryReturn() {
    }

    public FactoryReturn(String returnNo, long batchId, LocalDate returnDate, int filledQty, int rejectedQty, String rejectReason,
                         BigDecimal cost, String note) {
        this.returnNo = returnNo;
        this.batchId = batchId;
        this.returnDate = returnDate;
        this.filledQty = filledQty;
        this.rejectedQty = rejectedQty;
        this.rejectReason = rejectReason;
        this.cost = cost;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public int getFilledQty() {
        return filledQty;
    }

    public int getRejectedQty() {
        return rejectedQty;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
