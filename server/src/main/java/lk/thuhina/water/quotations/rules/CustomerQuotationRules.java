package lk.thuhina.water.quotations.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lk.thuhina.water.common.BusinessDates;

/**
 * Customer quotation rules (FR-64, FR-65, BR-20) with the prototype's messages (prototype store.js Ops.saveCQuote /
 * setCQuoteStatus / applyCQuotePrices): only a draft can change; only the next step (Draft → Sent → Accepted / Not accepted);
 * a sent quotation past its valid-until date shows Expired and can still be answered.
 */
public final class CustomerQuotationRules {

    public static final String MSG_TO = "Enter the organisation or person the quotation is for.";
    public static final String MSG_VALID = "Valid until must be on or after the quotation date.";
    public static final String MSG_TYPE = "Select the customer type (used for the standard prices).";
    public static final String MSG_NO_LINES = "Add at least one item to the quotation.";
    public static final String MSG_LINE = "Every line needs a description and a quantity of 1 or more.";
    public static final String MSG_PRICE = "Unit prices cannot be negative.";
    public static final String MSG_ONLY_DRAFT = "Only Draft quotations can be edited. Make a copy to change a sent quotation.";
    public static final String MSG_VIA = "Select how the quotation was sent.";
    public static final String MSG_REASON = "Enter the reason the quotation was not accepted.";
    public static final String MSG_LINK_FIRST = "Register or link the customer first.";
    public static final String MSG_ONLY_ACCEPTED = "Only an accepted quotation can set prices.";
    public static final String MSG_APPLIED = "The prices of this quotation are already applied.";
    public static final String MSG_NO_WATER = "This quotation has no water prices.";
    public static final String MSG_NOT_REGISTRABLE = "Only an accepted quotation to a new organisation can be registered as a customer.";
    public static final String MSG_SENT_BEFORE = "The date sent cannot be before the quotation date.";
    public static final String MSG_TERMS = "Choose the payment terms from the list.";

    /** Default validity of a new quotation. */
    public static final int VALID_DAYS = 30;
    /** Dashboard: sent quotations this close to expiry. */
    public static final int EXPIRY_WARNING_DAYS = 7;

    public static final String DEFAULT_CONDITIONS = String.join("\n",
            "Prices include delivery within our delivery areas.",
            "A bottle deposit applies to each new bottle given, unless an accepted old bottle is handed in.",
            "Water is filled and sealed by an SLS-certified filling factory.",
            "Prices are valid until the date shown above.");

    private CustomerQuotationRules() {
    }

    public enum Status {
        DRAFT("Draft"), SENT("Sent"), ACCEPTED("Accepted"), REJECTED("Not accepted");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Via {
        EMAIL("Email", "by email"), BY_HAND("By hand", "by hand"), POST("Post", "by post"), WHATSAPP("WhatsApp", "by WhatsApp");

        private final String label;
        private final String text;

        Via(String label, String text) {
            this.label = label;
            this.text = text;
        }

        public String label() {
            return label;
        }

        /** "by email", "by hand" … */
        public String text() {
            return text;
        }
    }

    public enum Kind {
        WATER, DEPOSIT, PRODUCT, OTHER
    }

    /** What the list and filter show: the status, or EXPIRED for a sent quotation past its date. */
    public static String shown(Status status, LocalDate validUntil, LocalDate today) {
        return status == Status.SENT && validUntil.isBefore(today) ? "EXPIRED" : status.name();
    }

    public static String shownLabel(String shown) {
        return "EXPIRED".equals(shown) ? "Expired" : Status.valueOf(shown).label();
    }

    /** The next step shown in the list (prototype "Next step"). */
    public static String nextStep(String shown, boolean customerLinked, boolean pricesApplied, boolean hasWater) {
        return switch (shown) {
            case "DRAFT" -> "Send to the customer";
            case "SENT" -> "Record the answer";
            case "EXPIRED" -> "Record the answer or make a new copy";
            case "ACCEPTED" -> !customerLinked ? "Register as customer" : !pricesApplied && hasWater ? "Apply prices to customer" : "–";
            default -> "–";
        };
    }

    /** Only the next step is allowed: Draft → Sent → Accepted / Not accepted. Null when the change is allowed. */
    public static String stepProblem(Status from, Status to) {
        boolean ok = switch (to) {
            case SENT -> from == Status.DRAFT;
            case ACCEPTED, REJECTED -> from == Status.SENT;
            default -> false;
        };
        return ok ? null : "Cannot change " + from.label() + " to " + to.label() + ".";
    }

    /** A line as the pop-up sends it. */
    public record Line(Kind kind, String description, Integer qty, BigDecimal unitPrice) {
    }

    /** The first problem with the header and lines, or null (dates are checked by the caller against today). */
    public static String problem(boolean toCustomer, String org, LocalDate date, LocalDate validUntil, boolean typeChosen, List<Line> lines) {
        if (!toCustomer && (org == null || org.isBlank())) {
            return MSG_TO;
        }
        if (validUntil == null || validUntil.isBefore(date)) {
            return MSG_VALID;
        }
        if (!typeChosen) {
            return MSG_TYPE;
        }
        if (lines.isEmpty()) {
            return MSG_NO_LINES;
        }
        for (Line l : lines) {
            if (l.qty() == null || l.qty() < 1 || l.description() == null || l.description().isBlank()) {
                return MSG_LINE;
            }
            if (l.unitPrice() == null || l.unitPrice().signum() < 0) {
                return MSG_PRICE;
            }
        }
        return null;
    }

    /** Date problems: not empty, not after today. */
    public static String dateProblem(LocalDate date, LocalDate today) {
        if (date == null) {
            return "Enter a valid date (DD/MM/YYYY).";
        }
        if (date.isAfter(today)) {
            return "Date cannot be after today (" + BusinessDates.format(today) + ").";
        }
        return null;
    }

    /** Amount of a line: qty × unit price. */
    public static BigDecimal amount(int qty, BigDecimal unitPrice) {
        return unitPrice.multiply(BigDecimal.valueOf(qty));
    }
}
