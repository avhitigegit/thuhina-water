package lk.thuhina.water.customers.model;

/** How a customer pays (FR-10, BR-09). Credit and Monthly bill need a credit limit and payment terms. */
public enum PayType {
    CASH("Cash"),
    CREDIT("Credit"),
    MONTHLY_BILL("Monthly bill");

    private final String label;

    PayType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
