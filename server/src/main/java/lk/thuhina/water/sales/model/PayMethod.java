package lk.thuhina.water.sales.model;

/**
 * How money was paid or received – customer payments, money paid on a bill, supplier, factory and expense payments.
 * A cheque or bank transfer needs its reference; {@link #referenceProblem} is the one rule for every payment.
 */
public enum PayMethod {
    CASH("Cash"),
    BANK_TRANSFER("Bank transfer"),
    CHEQUE("Cheque");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public static final String MSG_CHEQUE_NO = "Enter the cheque number.";
    public static final String MSG_BANK_REF = "Enter the bank reference.";

    public String label() {
        return label;
    }

    /** The message when the reference is missing (cheque number / bank reference), or null when it is fine. */
    public String referenceProblem(String reference) {
        if (this == CASH || (reference != null && !reference.isBlank())) {
            return null;
        }
        return this == CHEQUE ? MSG_CHEQUE_NO : MSG_BANK_REF;
    }
}
