package lk.thuhina.water.whatsapp.model;

/**
 * QUEUED → SENT → DELIVERED → READ, or FAILED (3 failed attempts, or Meta reports a failure). SKIPPED = never sent
 * (no valid WhatsApp number, or WhatsApp switched off before it went out). {@code rank} keeps webhook updates from going
 * backwards (a late "delivered" never replaces "read").
 */
public enum MessageStatus {
    QUEUED("Queued", 0),
    SENT("Sent", 1),
    DELIVERED("Delivered", 2),
    READ("Read", 3),
    FAILED("Failed", 4),
    SKIPPED("Skipped", 4);

    private final String label;
    private final int rank;

    MessageStatus(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }
}
