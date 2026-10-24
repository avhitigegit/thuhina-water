package lk.thuhina.water.masterdata.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * Another product sold besides water (FR-02): dispensers, pumps, stands … The stock is set only when the product is
 * created (opening stock); later it changes only through goods receipts, sales and stock adjustments.
 */
@Entity
@Table(name = "product")
public class Product extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "cost_price", precision = 12, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "stock_qty", nullable = false)
    private int stockQty;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
    }

    public Product(String code, String name, BigDecimal sellingPrice, BigDecimal costPrice, int openingStock, boolean active) {
        this.code = code;
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.stockQty = openingStock;
        this.active = active;
    }

    /** Edit – everything except the code and the stock. */
    public void update(String name, BigDecimal sellingPrice, BigDecimal costPrice, boolean active) {
        this.name = name;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public int getStockQty() {
        return stockQty;
    }

    public boolean isActive() {
        return active;
    }
}
