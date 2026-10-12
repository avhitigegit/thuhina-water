package lk.thuhina.water.purchasing.model;

/** Purchase order status (FR-19): Draft → Approved → Sent → Partly received / Received; Cancelled from Draft or Approved. */
public enum PoStatus {
    DRAFT("Draft"),
    APPROVED("Approved"),
    SENT("Sent"),
    PARTLY_RECEIVED("Partly received"),
    RECEIVED("Received"),
    CANCELLED("Cancelled");

    private final String label;

    PoStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
