package lk.thuhina.water.purchasing.model;

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
import lk.thuhina.water.sales.model.PayMethod;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Money paid to a supplier (FR-21), optionally against a PO. Insert-only. */
@Entity
@Immutable
@Table(name = "supplier_payment")
@EntityListeners(AuditingEntityListener.class)
public class SupplierPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sp_no", nullable = false, unique = true, length = 20)
    private String spNo;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "po_id")
    private Long poId;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayMethod method;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected SupplierPayment() {
    }

    public SupplierPayment(String spNo, long supplierId, Long poId, LocalDate payDate, BigDecimal amount, PayMethod method,
                           String reference, String note) {
        this.spNo = spNo;
        this.supplierId = supplierId;
        this.poId = poId;
        this.payDate = payDate;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getSpNo() {
        return spNo;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getPoId() {
        return poId;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
