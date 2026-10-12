package lk.thuhina.water.purchasing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A goods receipt (FR-20): items received against a sent PO, with the damaged ones. {@code value} = good × price, which
 * is what the supplier is owed. The stock change is a ledger posting with source GRN. Insert-only.
 */
@Entity
@Immutable
@Table(name = "goods_receipt")
@EntityListeners(AuditingEntityListener.class)
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grn_no", nullable = false, unique = true, length = 20)
    private String grnNo;

    @Column(name = "po_id", nullable = false)
    private Long poId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "delivery_note", length = 50)
    private String deliveryNote;

    @Column(length = 300)
    private String note;

    @Column(nullable = false)
    private BigDecimal value;

    @OneToMany(mappedBy = "grn", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<GrnLine> lines = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected GoodsReceipt() {
    }

    public GoodsReceipt(String grnNo, long poId, long supplierId, LocalDate receivedDate, String deliveryNote, String note) {
        this.grnNo = grnNo;
        this.poId = poId;
        this.supplierId = supplierId;
        this.receivedDate = receivedDate;
        this.deliveryNote = deliveryNote;
        this.note = note;
        this.value = BigDecimal.ZERO;
    }

    /** Adds a line; the value grows by good × price. */
    public void addLine(long poLineId, ItemType type, String code, int received, int damaged, BigDecimal unitPrice) {
        GrnLine l = new GrnLine(this, poLineId, type, code, received, damaged, unitPrice);
        lines.add(l);
        value = value.add(l.value());
    }

    public Long getId() {
        return id;
    }

    public String getGrnNo() {
        return grnNo;
    }

    public Long getPoId() {
        return poId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public String getDeliveryNote() {
        return deliveryNote;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getValue() {
        return value;
    }

    public List<GrnLine> getLines() {
        return lines;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
