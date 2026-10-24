package lk.thuhina.water.partners.model;

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

/** An item a supplier supplies: an empty bottle (bottle type code, e.g. B20) or a product (product code, e.g. P04). */
@Entity
@Table(name = "supplier_item")
public class SupplierItem {

    public enum ItemType { BOTTLE, PRODUCT }

    /** Type + code of an item, e.g. (BOTTLE, B20). */
    public record Ref(ItemType type, String code) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 10)
    private ItemType itemType;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    protected SupplierItem() {
    }

    SupplierItem(Supplier supplier, ItemType itemType, String itemCode) {
        this.supplier = supplier;
        this.itemType = itemType;
        this.itemCode = itemCode;
    }

    public Ref ref() {
        return new Ref(itemType, itemCode);
    }

    public ItemType getItemType() {
        return itemType;
    }

    public String getItemCode() {
        return itemCode;
    }
}
