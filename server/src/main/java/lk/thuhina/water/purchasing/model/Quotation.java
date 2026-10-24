package lk.thuhina.water.purchasing.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * A supplier's quotation for a request (FR-16): reference, dates, delivery time, a unit price per requested item (null =
 * not quoted), notes and an optional scanned file. The owner chooses one per request, with a reason (FR-17).
 */
@Entity
@Table(name = "quotation")
public class Quotation extends BaseEntity {

    @Column(name = "quote_no", nullable = false, unique = true, length = 20, updatable = false)
    private String quoteNo;

    @Column(name = "qr_id", nullable = false, updatable = false)
    private Long qrId;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @Column(name = "supplier_ref", length = 50, updatable = false)
    private String supplierRef;

    @Column(name = "quote_date", nullable = false, updatable = false)
    private LocalDate quoteDate;

    @Column(name = "valid_until", nullable = false, updatable = false)
    private LocalDate validUntil;

    @Column(name = "delivery_days", nullable = false, updatable = false)
    private int deliveryDays;

    @Column(length = 300, updatable = false)
    private String notes;

    @Column(name = "attachment_path", length = 200)
    private String attachmentPath;

    @Column(name = "attachment_name", length = 120)
    private String attachmentName;

    @Column(nullable = false)
    private boolean selected;

    @Column(name = "selection_reason", length = 300)
    private String selectionReason;

    @Column(name = "selected_by", length = 50)
    private String selectedBy;

    @Column(name = "selected_on")
    private LocalDate selectedOn;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuotationLine> lines = new ArrayList<>();

    protected Quotation() {
    }

    public Quotation(String quoteNo, long qrId, long supplierId, String supplierRef, LocalDate quoteDate, LocalDate validUntil,
                     int deliveryDays, String notes) {
        this.quoteNo = quoteNo;
        this.qrId = qrId;
        this.supplierId = supplierId;
        this.supplierRef = supplierRef;
        this.quoteDate = quoteDate;
        this.validUntil = validUntil;
        this.deliveryDays = deliveryDays;
        this.notes = notes;
    }

    public void addLine(String itemCode, BigDecimal unitPrice) {
        lines.add(new QuotationLine(this, itemCode, unitPrice));
    }

    public void choose(String reason, String username, LocalDate on) {
        this.selected = true;
        this.selectionReason = reason;
        this.selectedBy = username;
        this.selectedOn = on;
    }

    public void attach(String path, String name) {
        this.attachmentPath = path;
        this.attachmentName = name;
    }

    /** The quoted unit price of an item, or null when not quoted. */
    public BigDecimal priceOf(String itemCode) {
        return lines.stream().filter(l -> l.getItemCode().equals(itemCode)).map(QuotationLine::getUnitPrice).filter(p -> p != null)
                .findFirst().orElse(null);
    }

    public String getQuoteNo() {
        return quoteNo;
    }

    public Long getQrId() {
        return qrId;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierRef() {
        return supplierRef;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public int getDeliveryDays() {
        return deliveryDays;
    }

    public String getNotes() {
        return notes;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public boolean isSelected() {
        return selected;
    }

    public String getSelectionReason() {
        return selectionReason;
    }

    public String getSelectedBy() {
        return selectedBy;
    }

    public LocalDate getSelectedOn() {
        return selectedOn;
    }

    public List<QuotationLine> getLines() {
        return lines;
    }
}
