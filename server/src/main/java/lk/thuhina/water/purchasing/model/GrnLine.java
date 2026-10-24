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
import lk.thuhina.water.common.Money;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import org.hibernate.annotations.Immutable;

/** A received item on a goods receipt: received now, damaged on arrival, and the PO's unit price. */
@Entity
@Immutable
@Table(name = "grn_line")
public class GrnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grn_id")
    private GoodsReceipt grn;

    @Column(name = "po_line_id", nullable = false)
    private Long poLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType itemType;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    @Column(nullable = false)
    private int received;

    @Column(nullable = false)
    private int damaged;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    protected GrnLine() {
    }

    GrnLine(GoodsReceipt grn, long poLineId, ItemType itemType, String itemCode, int received, int damaged, BigDecimal unitPrice) {
        this.grn = grn;
        this.poLineId = poLineId;
        this.itemType = itemType;
        this.itemCode = itemCode;
        this.received = received;
        this.damaged = damaged;
        this.unitPrice = unitPrice;
    }

    /** Good items = received − damaged. */
    public int good() {
        return received - damaged;
    }

    public BigDecimal value() {
        return Money.times(unitPrice, good());
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getItemCode() {
        return itemCode;
    }

    public int getReceived() {
        return received;
    }

    public int getDamaged() {
        return damaged;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
