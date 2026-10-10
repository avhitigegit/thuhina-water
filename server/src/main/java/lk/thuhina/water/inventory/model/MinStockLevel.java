package lk.thuhina.water.inventory.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Minimum filled bottles in store for a bottle type (FR-31): below it the low-stock alert shows. */
@Entity
@Table(name = "min_stock_level")
public class MinStockLevel {

    @Id
    @Column(name = "bottle_type_code", length = 10)
    private String bottleTypeCode;

    @Column(name = "min_filled", nullable = false)
    private int minFilled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    protected MinStockLevel() {
    }

    public MinStockLevel(String bottleTypeCode) {
        this.bottleTypeCode = bottleTypeCode;
    }

    public void set(int minFilled, Instant at, String by) {
        this.minFilled = minFilled;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public int getMinFilled() {
        return minFilled;
    }
}
