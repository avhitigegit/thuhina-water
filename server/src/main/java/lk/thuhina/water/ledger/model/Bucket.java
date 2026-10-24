package lk.thuhina.water.ledger.model;

/** The five places a bottle can be (design 4.3 {@code stock_balance.bucket}), with the words used on screens and messages. */
public enum Bucket {
    EMPTY("Empty in store"),
    FACTORY("At factory"),
    FILLED("Filled in store"),
    CUSTOMERS("With customers"),
    WRITTEN_OFF("Written off");

    private final String label;

    Bucket(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** In circulation = every bucket except Written off. */
    public boolean inCirculation() {
        return this != WRITTEN_OFF;
    }
}
