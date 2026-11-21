package lk.thuhina.water.sales.model;

/**
 * How money was paid or received – customer payments, money paid on a bill, supplier, factory and expense payments
 * (client 21/11/2026: Card, Cash, Online transfer, Cheque, Bill to Bill). {@code BANK_TRANSFER} keeps its stored name
 * (insert-only ledgers already hold it) and reads "Online transfer".
 * <p>
 * The reference: a cheque needs its number, an online transfer its reference and Bill to Bill the number of the earlier
 * bill it pays; a card slip number is optional; cash has none. {@link #referenceProblem} is the one rule for every payment.
 */
public enum PayMethod {
    CASH("Cash", null, null),
    CARD("Card", "Card slip number", null),
    BANK_TRANSFER("Online transfer", "Transfer reference", "Enter the transfer reference."),
    CHEQUE("Cheque", "Cheque number", "Enter the cheque number."),
    BILL_TO_BILL("Bill to Bill", "Bill number it pays", "Enter the number of the bill it pays.");

    private final String label;
    private final String referenceLabel;
    private final String missingMessage;

    PayMethod(String label, String referenceLabel, String missingMessage) {
        this.label = label;
        this.referenceLabel = referenceLabel;
        this.missingMessage = missingMessage;
    }

    public static final String MSG_CHEQUE_NO = CHEQUE.missingMessage;
    public static final String MSG_BANK_REF = BANK_TRANSFER.missingMessage;
    public static final String MSG_BILL_NO = BILL_TO_BILL.missingMessage;

    public String label() {
        return label;
    }

    /** "Cheque number", "Transfer reference", … or null for cash. */
    public String referenceLabel() {
        return referenceLabel;
    }

    /** Whether a reference is kept at all (everything but cash). */
    public boolean hasReference() {
        return referenceLabel != null;
    }

    /** Whether the reference must be given (online transfer, cheque, Bill to Bill). */
    public boolean needsReference() {
        return missingMessage != null;
    }

    /** The reference to store: none for cash. */
    public String keep(String reference) {
        return hasReference() ? reference : null;
    }

    /** The message when the reference is missing, or null when it is fine. */
    public String referenceProblem(String reference) {
        if (!needsReference() || (reference != null && !reference.isBlank())) {
            return null;
        }
        return missingMessage;
    }
}
