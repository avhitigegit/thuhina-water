package lk.thuhina.water.inventory.model;

import lk.thuhina.water.ledger.model.Bucket;

/** Where company damage happened (FR-29, BR-05) and the bucket the bottles are taken from. */
public enum DamageLocation {
    EMPTY_IN_STORE("Empty in store", Bucket.EMPTY),
    FILLED_IN_STORE("Filled in store", Bucket.FILLED),
    AT_FACTORY("At factory", Bucket.FACTORY),
    /** Bottles broken on the way to customers are taken from Filled in store. */
    DURING_DELIVERY("During delivery", Bucket.FILLED);

    private final String label;
    private final Bucket bucket;

    DamageLocation(String label, Bucket bucket) {
        this.label = label;
        this.bucket = bucket;
    }

    public String label() {
        return label;
    }

    public Bucket bucket() {
        return bucket;
    }
}
