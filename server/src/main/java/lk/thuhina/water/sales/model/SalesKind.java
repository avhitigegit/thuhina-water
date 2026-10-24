package lk.thuhina.water.sales.model;

/** Kinds of sales transaction (design 4.3 {@code sales_txn.kind}) with the names shown on screens, bills and messages. */
public enum SalesKind {
    FIRST_PURCHASE("First purchase"),
    EXCHANGE("Exchange"),
    LEFT_AT_DOOR("Left at door"),
    COLLECT_EMPTIES("Empties collected"),
    EXTRA_ORDER("Extra order"),
    PRODUCT_SALE("Product sale"),
    CUSTOMER_DAMAGE("Customer damage"),
    OWED_SETTLEMENT("Bottles owed settled"),
    OPENING_BALANCE("Opening balance"),
    REVERSAL("Reversal");

    private final String label;

    SalesKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Delivery bills move the next delivery date on (design 5.4); so does a first purchase. */
    public boolean advancesSchedule() {
        return this == EXCHANGE || this == LEFT_AT_DOOR || this == FIRST_PURCHASE;
    }

    /** BR-14: reversals and opening balances cannot be reversed. */
    public boolean reversible() {
        return this != REVERSAL && this != OPENING_BALANCE;
    }
}
