package lk.thuhina.water.sales.model;

/** How money paid on a bill was received. */
public enum PayMethod {
    CASH("Cash"),
    BANK_TRANSFER("Bank transfer"),
    CHEQUE("Cheque");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
