package lk.thuhina.water.ledger.model;

import java.util.Objects;

/**
 * One change made by a posting (design 5.1): bottles of a type into / out of a bucket, or product stock up / down.
 * M05 adds the target CUSTOMER_BOTTLE (held / to collect / owed per customer).
 */
public record Effect(Target target, String bottleTypeCode, Bucket bucket, String productCode, int delta) {

    public enum Target { STOCK, PRODUCT }

    public Effect {
        Objects.requireNonNull(target, "target");
        if (target == Target.STOCK && (bottleTypeCode == null || bucket == null)) {
            throw new IllegalArgumentException("A stock effect needs a bottle type and a bucket");
        }
        if (target == Target.PRODUCT && productCode == null) {
            throw new IllegalArgumentException("A product effect needs a product code");
        }
    }

    /** Bottles of {@code bottleTypeCode} in {@code bucket} change by {@code delta}. */
    public static Effect stock(String bottleTypeCode, Bucket bucket, int delta) {
        return new Effect(Target.STOCK, bottleTypeCode, bucket, null, delta);
    }

    /** Stock of the product changes by {@code delta}. */
    public static Effect product(String productCode, int delta) {
        return new Effect(Target.PRODUCT, null, null, productCode, delta);
    }

    public Effect negated() {
        return new Effect(target, bottleTypeCode, bucket, productCode, -delta);
    }
}
