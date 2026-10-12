package lk.thuhina.water.purchasing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;

/**
 * A purchase order (FR-18, FR-19), made only from the chosen quotation (BR-10). Lines and dates can be changed while it is a
 * Draft; every status change is kept in {@code po_status_history}. Receipts add to each line's received / damaged.
 */
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends BaseEntity {

    @Column(name = "po_no", nullable = false, unique = true, length = 20, updatable = false)
    private String poNo;

    @Column(name = "qr_id", nullable = false, updatable = false)
    private Long qrId;

    @Column(name = "quotation_id", nullable = false, updatable = false)
    private Long quotationId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "po_date", nullable = false, updatable = false)
    private LocalDate poDate;

    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.DRAFT;

    @Column(length = 300)
    private String notes;

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "sent_on")
    private LocalDate sentOn;

    @Column(name = "cancel_reason", length = 300)
    private String cancelReason;

    @OneToMany(mappedBy = "po", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PoLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "po", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<PoStatusHistory> history = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public PurchaseOrder(String poNo, long qrId, long quotationId, long supplierId, LocalDate poDate, LocalDate expectedDate) {
        this.poNo = poNo;
        this.qrId = qrId;
        this.quotationId = quotationId;
        this.supplierId = supplierId;
        this.poDate = poDate;
        this.expectedDate = expectedDate;
    }

    public void addLine(ItemType type, String code, int qty, BigDecimal unitPrice) {
        int next = lines.stream().mapToInt(PoLine::getLineNo).max().orElse(0) + 1;
        lines.add(new PoLine(this, next, type, code, qty, unitPrice));
    }

    /** Draft edit: lines missing from {@code keep} are removed. */
    public void removeLinesNotIn(List<String> keep) {
        lines.removeIf(l -> !keep.contains(l.getItemCode()));
    }

    public void edit(LocalDate expectedDate, String notes) {
        this.expectedDate = expectedDate;
        this.notes = notes;
    }

    /** Moves to {@code to} and records it in the history. */
    public void moveTo(PoStatus to, String username, Instant ts, String note) {
        this.status = to;
        history.add(new PoStatusHistory(this, to, ts, username, note));
    }

    public void approved(String username, LocalDate on) {
        this.approvedBy = username;
        this.approvedOn = on;
    }

    public void sent(LocalDate on) {
        this.sentOn = on;
    }

    public void cancelled(String reason) {
        this.cancelReason = reason;
    }

    public BigDecimal total() {
        return Money.of(lines.stream().map(l -> Money.times(l.getUnitPrice(), l.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public PoLine line(String itemCode) {
        return lines.stream().filter(l -> l.getItemCode().equals(itemCode)).findFirst().orElse(null);
    }

    public String getPoNo() {
        return poNo;
    }

    public Long getQrId() {
        return qrId;
    }

    public Long getQuotationId() {
        return quotationId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public LocalDate getPoDate() {
        return poDate;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public PoStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDate getApprovedOn() {
        return approvedOn;
    }

    public LocalDate getSentOn() {
        return sentOn;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<PoLine> getLines() {
        return lines;
    }

    public List<PoStatusHistory> getHistory() {
        return history;
    }
}
