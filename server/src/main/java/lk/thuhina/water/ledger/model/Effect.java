package lk.thuhina.water.ledger.model;

import java.util.Objects;

/**
 * One change made by a posting (design 5.1): bottles of a type into / out of a bucket, product stock up / down, or a
 * customer's bottle balance (held / to collect / owed) per bottle type.
 */
public record Effect(Target target, String bottleTypeCode, Bucket bucket, String productCode, Long customerId,
                     CustomerField customerField, int delta) {

    public enum Target { STOCK, CUSTOMER_BOTTLE, PRODUCT }

    public Effect {
        Objects.requireNonNull(target, "target");
        if (target == Target.STOCK && (bottleTypeCode == null || bucket == null)) {
            throw new IllegalArgumentException("A stock effect needs a bottle type and a bucket");
        }
        if (target == Target.PRODUCT && productCode == null) {
            throw new IllegalArgumentException("A product effect needs a product code");
        }
        if (target == Target.CUSTOMER_BOTTLE && (customerId == null || bottleTypeCode == null || customerField == null)) {
            throw new IllegalArgumentException("A customer bottle effect needs a customer, a bottle type and a field");
        }
    }

    /** Bottles of {@code bottleTypeCode} in {@code bucket} change by {@code delta}. */
    public static Effect stock(String bottleTypeCode, Bucket bucket, int delta) {
        return new Effect(Target.STOCK, bottleTypeCode, bucket, null, null, null, delta);
    }

    /** Stock of the product changes by {@code delta}. */
    public static Effect product(String productCode, int delta) {
        return new Effect(Target.PRODUCT, null, null, productCode, null, null, delta);
    }

    /** A customer's held / to collect / owed bottles of {@code bottleTypeCode} change by {@code delta}. */
    public static Effect customer(long customerId, String bottleTypeCode, CustomerField field, int delta) {
        return new Effect(Target.CUSTOMER_BOTTLE, bottleTypeCode, null, null, customerId, field, delta);
    }

    public Effect negated() {
        return new Effect(target, bottleTypeCode, bucket, productCode, customerId, customerField, -delta);
    }
}
