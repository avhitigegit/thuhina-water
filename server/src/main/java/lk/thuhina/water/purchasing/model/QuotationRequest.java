package lk.thuhina.water.purchasing.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import org.hibernate.annotations.BatchSize;

/** A quotation request (FR-15): the items and quantities wanted and the suppliers asked to quote. */
@Entity
@Table(name = "quotation_request")
public class QuotationRequest extends BaseEntity {

    @Column(name = "qr_no", nullable = false, unique = true, length = 20, updatable = false)
    private String qrNo;

    @Column(name = "request_date", nullable = false, updatable = false)
    private LocalDate requestDate;

    @Column(name = "required_by", updatable = false)
    private LocalDate requiredBy;

    @Column(length = 300, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QrStatus status = QrStatus.OPEN;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<QrItem> items = new ArrayList<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "qr_supplier", joinColumns = @JoinColumn(name = "qr_id"))
    @Column(name = "supplier_id")
    private Set<Long> supplierIds = new LinkedHashSet<>();

    protected QuotationRequest() {
    }

    public QuotationRequest(String qrNo, LocalDate requestDate, LocalDate requiredBy, String note, Set<Long> supplierIds) {
        this.qrNo = qrNo;
        this.requestDate = requestDate;
        this.requiredBy = requiredBy;
        this.note = note;
        this.supplierIds.addAll(supplierIds);
    }

    public void addItem(ItemType type, String code, int qty) {
        items.add(new QrItem(this, items.size() + 1, type, code, qty));
    }

    public void setStatus(QrStatus status) {
        this.status = status;
    }

    public String getQrNo() {
        return qrNo;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public LocalDate getRequiredBy() {
        return requiredBy;
    }

    public String getNote() {
        return note;
    }

    public QrStatus getStatus() {
        return status;
    }

    public List<QrItem> getItems() {
        return items;
    }

    public Set<Long> getSupplierIds() {
        return supplierIds;
    }
}
