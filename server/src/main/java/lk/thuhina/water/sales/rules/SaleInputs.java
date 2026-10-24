package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;
import java.util.Map;

/** Plain inputs of the rule classes, read by the services from the database (no Spring, no JPA). */
public final class SaleInputs {

    private SaleInputs() {
    }

    /** The customer with their bottles per bottle type; {@code id} = null for a walk-in. */
    public record CustomerView(Long id, String name, String typeName, Map<String, Bottles> bottles) {

        public Bottles bottles(String bottleTypeCode) {
            return bottles.getOrDefault(bottleTypeCode, Bottles.NONE);
        }

        public int totalHeld() {
            return bottles.values().stream().mapToInt(Bottles::held).sum();
        }
    }

    /** A bottle type: code, short label "20L" and name "20L Bottle". */
    public record BottleInfo(String code, String label, String name) {
    }

    /** Water price on the bill date and whether it is the customer's agreed price. */
    public record Price(BigDecimal price, boolean agreed) {
    }

    /** An old-bottle brand (BR-02) and the bottle type it is accepted for. */
    public record BrandInfo(long id, String name, String bottleTypeCode, String bottleName, boolean active) {
    }

    /** A product with its selling price, cost and current stock. */
    public record ProductInfo(String code, String name, BigDecimal sellingPrice, BigDecimal costPrice, boolean active, int stock) {
    }
}
