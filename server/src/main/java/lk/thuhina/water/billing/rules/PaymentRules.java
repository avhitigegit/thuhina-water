package lk.thuhina.water.billing.rules;

import java.math.BigDecimal;

import lk.thuhina.water.sales.model.PayMethod;

/** Customer payment rules (FR-50, prototype {@code Ops.receivePayment}). */
public final class PaymentRules {

    public static final String MSG_CUSTOMER = "Select a customer.";
    public static final String MSG_AMOUNT = "Enter the amount received.";
    public static final String MSG_METHOD = "Select how the money was received.";
    public static final String MSG_BANK_REF = "Enter the bank reference.";
    public static final String MSG_CHEQUE_NO = "Enter the cheque number.";

    private PaymentRules() {
    }

    /** The reference message when it is missing: bank transfer and cheque need one, cash does not. Null = fine. */
    public static String referenceProblem(PayMethod method, String reference) {
        if (method == null || method == PayMethod.CASH || (reference != null && !reference.isBlank())) {
            return null;
        }
        return method == PayMethod.CHEQUE ? MSG_CHEQUE_NO : MSG_BANK_REF;
    }

    /** Less than the balance owed: "Part payment". */
    public static boolean isPartPayment(BigDecimal amount, BigDecimal balanceBefore) {
        return amount.compareTo(balanceBefore) < 0;
    }

    /** More than the balance owed: the rest is an advance (credit balance). */
    public static boolean isAdvance(BigDecimal amount, BigDecimal balanceBefore) {
        return amount.compareTo(balanceBefore.max(BigDecimal.ZERO)) > 0;
    }

    /** "Cheque (Cheque 330981)", "Bank transfer (TRF-771200)", "Cash". */
    public static String how(PayMethod method, String reference) {
        return method.label() + (reference == null || reference.isBlank() ? "" : " (" + reference + ")");
    }
}
