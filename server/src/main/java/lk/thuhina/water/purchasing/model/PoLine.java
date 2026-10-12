package lk.thuhina.water.purchasing.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;

/** A PO line: item, quantity ordered, unit price, and how many have been received (damaged included) so far. */
@Entity
@Table(name = "po_line")
public class PoLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id")
    private PurchaseOrder po;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType itemType;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    @Column(nullable = false)
    private int qty;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "received_qty", nullable = false)
    private int receivedQty;

    @Column(name = "damaged_qty", nullable = false)
    private int damagedQty;

    protected PoLine() {
    }

    PoLine(PurchaseOrder po, int lineNo, ItemType itemType, String itemCode, int qty, BigDecimal unitPrice) {
        this.po = po;
        this.lineNo = lineNo;
        this.itemType = itemType;
        this.itemCode = itemCode;
        this.qty = qty;
        this.unitPrice = unitPrice;
    }

    public void change(int qty, BigDecimal unitPrice) {
        this.qty = qty;
        this.unitPrice = unitPrice;
    }

    public void receive(int received, int damaged) {
        this.receivedQty += received;
        this.damagedQty += damaged;
    }

    /** Still to come = ordered − received (damaged items count as received; they are not re-ordered). */
    public int stillDue() {
        return qty - receivedQty;
    }

    public Long getId() {
        return id;
    }

    public int getLineNo() {
        return lineNo;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getQty() {
        return qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getReceivedQty() {
        return receivedQty;
    }

    public int getDamagedQty() {
        return damagedQty;
    }
}
