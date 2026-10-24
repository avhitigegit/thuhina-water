package lk.thuhina.water.purchasing.model;

/** Quotation request status (design 4.3): asked → quotations in → one chosen → closed when its PO is made. */
public enum QrStatus {
    OPEN("Open"),
    QUOTES_RECEIVED("Quotes received"),
    SELECTED("Selected"),
    CLOSED("Closed");

    private final String label;

    QrStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Quotations can still be added and one chosen. */
    public boolean isOpen() {
        return this == OPEN || this == QUOTES_RECEIVED;
    }
}
