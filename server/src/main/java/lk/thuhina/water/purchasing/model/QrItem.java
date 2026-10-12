package lk.thuhina.water.purchasing.model;

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

/** An item asked for on a quotation request: empty bottles (bottle type code) or a product, with the quantity. */
@Entity
@Table(name = "qr_item")
public class QrItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qr_id")
    private QuotationRequest request;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType itemType;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    @Column(nullable = false)
    private int qty;

    protected QrItem() {
    }

    QrItem(QuotationRequest request, int lineNo, ItemType itemType, String itemCode, int qty) {
        this.request = request;
        this.lineNo = lineNo;
        this.itemType = itemType;
        this.itemCode = itemCode;
        this.qty = qty;
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
}
