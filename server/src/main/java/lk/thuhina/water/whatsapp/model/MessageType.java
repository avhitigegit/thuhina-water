package lk.thuhina.water.whatsapp.model;

import java.util.List;

/**
 * The WhatsApp messages the system sends (FR-61, design 9.2) and the Meta template each one uses. {@code body} is the text
 * to create the template with in Meta's WhatsApp Manager (category Utility, {{1}}, {{2}} … = {@code params} in order);
 * {@code document} = the template has a Document header (the PDF is attached when it is sent).
 */
public enum MessageType {
    WELCOME("Welcome", "welcome_v1", false,
            "Welcome to Thuhina Water, {{1}}! Your customer code is {{2}}. We deliver to you on {{3}}, and your first delivery is on "
                    + "{{4}}. Your bills and reminders will come to this number. Thank you for choosing us.",
            List.of("Customer name", "Customer code", "Delivery day", "First delivery date")),
    QUOTATION("Quotation", "quotation_v1", true,
            "Dear {{1}}, please find attached our quotation {{2}}. The prices are valid until {{3}}. Please contact us if you have "
                    + "any questions. Thank you – Thuhina Water.",
            List.of("Organisation name", "Quotation number", "Valid until")),
    DELIVERY_BILL("Delivery bill", "delivery_bill_v1", true,
            "Dear {{1}}, {{2}}.\n\nBill: {{3}} ({{4}})\nBottles left: {{5}}\nEmpties collected: {{6}}\nAmount: {{7}}\nPaid: {{8}}\n"
                    + "{{9}}\nNext delivery: {{10}}\n\nThank you – Thuhina Water.",
            List.of("Customer name", "Opening line (\"here is the bill for your delivery\" or \"nobody was home, so we left your "
                    + "water at the door. Here is the bill\")", "Bill number", "Bill date", "Bottles left", "Empties collected",
                    "Amount", "Paid", "Balance (or \"Payment: on your monthly invoice\")", "Next delivery date")),
    BILL_CANCELLED("Bill cancelled", "bill_cancelled_v1", false,
            "Dear {{1}}, bill {{2}} of {{3}} for {{4}} was cancelled. {{5}}. Please contact us if you have any questions. "
                    + "Thank you – Thuhina Water.",
            List.of("Customer name", "Bill number", "Bill date", "Amount", "Balance now")),
    PAYMENT_RECEIPT("Payment receipt", "payment_receipt_v1", false,
            "Dear {{1}}, thank you for your payment of {{2}} by {{3}} on {{4}}. Receipt number: {{5}}. {{6}}. Thank you – "
                    + "Thuhina Water.",
            List.of("Customer name", "Amount", "Method", "Date", "Receipt number", "Balance now")),
    MONTHLY_INVOICE("Monthly invoice", "monthly_invoice_v1", true,
            "Dear {{1}}, your Thuhina Water invoice for {{2}} is attached. Total due: {{3}}. Please pay by {{4}}. Thank you.",
            List.of("Customer name", "Month", "Total due", "Due date")),
    PAYMENT_DUE_SOON("Payment due soon", "payment_due_soon_v1", false,
            "Dear {{1}}, this is a reminder that {{2}} is due on {{3}}. Please pay by then. If you have already paid, please "
                    + "ignore this message. Thank you – Thuhina Water.",
            List.of("Customer name", "Amount", "Due date")),
    PAYMENT_OVERDUE("Payment overdue", "payment_overdue_v1", false,
            "Dear {{1}}, your payment of {{2}} was due on {{3}} and is still unpaid. Please pay as soon as possible. If you have "
                    + "already paid, please ignore this message. Thank you – Thuhina Water.",
            List.of("Customer name", "Amount", "Due date")),
    /** "Test send" from the WhatsApp tab – Meta's ready-made {@code hello_world} template (every account has it). */
    TEST("Test message", "hello_world", false, "Hello World", List.of());

    private final String label;
    private final String template;
    private final boolean document;
    private final String body;
    private final List<String> params;

    MessageType(String label, String template, boolean document, String body, List<String> params) {
        this.label = label;
        this.template = template;
        this.document = document;
        this.body = body;
        this.params = params;
    }

    public String label() {
        return label;
    }

    public String template() {
        return template;
    }

    public boolean document() {
        return document;
    }

    public String body() {
        return body;
    }

    public List<String> params() {
        return params;
    }
}
