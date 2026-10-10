package lk.thuhina.water.ledger.model;

/** What caused a posting (design 4.3 {@code ledger_effect.source_type}). */
public enum SourceType {
    /** A sales transaction / bill (M06). */
    SALES_TXN,
    /** A goods receipt (M09). */
    GRN,
    /** A factory batch sent (M10). */
    BATCH,
    /** Filled bottles returned by a factory (M10). */
    RETURN,
    DAMAGE,
    ADJUSTMENT,
    /** Opening stock (seed, data migration, a new product's opening stock). */
    IMPORT
}
