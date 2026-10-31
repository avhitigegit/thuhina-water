package lk.thuhina.water.whatsapp.model;

/** The WhatsApp messages the system sends (design 9.2) and the Meta template each one uses. */
public enum MessageType {
    LEFT_AT_DOOR("Left at door", "left_at_door_v1"),
    PAYMENT_REMINDER("Payment reminder", "payment_reminder_v1"),
    MONTHLY_INVOICE("Monthly invoice", "monthly_invoice_v1"),
    /** "Test send" from the WhatsApp tab – Meta's ready-made {@code hello_world} template (every account has it). */
    TEST("Test message", "hello_world");

    private final String label;
    private final String template;

    MessageType(String label, String template) {
        this.label = label;
        this.template = template;
    }

    public String label() {
        return label;
    }

    public String template() {
        return template;
    }
}
