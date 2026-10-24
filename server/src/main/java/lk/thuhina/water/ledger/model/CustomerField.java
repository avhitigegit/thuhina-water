package lk.thuhina.water.ledger.model;

/** A customer's bottle balance per bottle type (design 4.3 {@code customer_bottle_balance}), with the words for messages. */
public enum CustomerField {
    HELD("held", "Bottles held"),
    TO_COLLECT("to_collect", "Empties to collect"),
    OWED("owed", "Bottles owed");

    private final String column;
    private final String label;

    CustomerField(String column, String label) {
        this.column = column;
        this.label = label;
    }

    /** Column in {@code customer_bottle_balance}. */
    public String column() {
        return column;
    }

    public String label() {
        return label;
    }
}
