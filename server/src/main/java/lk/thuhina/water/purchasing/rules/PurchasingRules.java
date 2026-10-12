package lk.thuhina.water.purchasing.rules;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.ErrorCodes;
import lk.thuhina.water.purchasing.model.PoStatus;
import lk.thuhina.water.sales.model.PayMethod;

/**
 * Purchasing rules without the database (FR-17 – FR-21, BR-10): PO steps, the next step shown on the list, lowest-price
 * flags for the comparison, receipt line checks and the payment reference rule.
 */
public final class PurchasingRules {

    public static final String MSG_REFERENCE = "Enter the cheque number or bank reference.";

    /** Allowed moves: Draft → Approved → Sent → Partly received / Received; Cancelled only from Draft or Approved. */
    private static final Map<PoStatus, Set<PoStatus>> ALLOWED = Map.of(
            PoStatus.DRAFT, EnumSet.of(PoStatus.APPROVED, PoStatus.CANCELLED),
            PoStatus.APPROVED, EnumSet.of(PoStatus.SENT, PoStatus.CANCELLED),
            PoStatus.SENT, EnumSet.of(PoStatus.PARTLY_RECEIVED, PoStatus.RECEIVED),
            PoStatus.PARTLY_RECEIVED, EnumSet.of(PoStatus.PARTLY_RECEIVED, PoStatus.RECEIVED),
            PoStatus.RECEIVED, EnumSet.noneOf(PoStatus.class),
            PoStatus.CANCELLED, EnumSet.noneOf(PoStatus.class));

    private PurchasingRules() {
    }

    public static boolean canMove(PoStatus from, PoStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    /** 422 INVALID_STATE "Cannot change Approved to Received." when the move is not allowed. */
    public static void checkMove(PoStatus from, PoStatus to) {
        if (!canMove(from, to)) {
            throw new BusinessException(ErrorCodes.INVALID_STATE, "Cannot change " + from.label() + " to " + to.label() + ".",
                    Map.of("from", from.name(), "to", to.name()));
        }
    }

    /** The one action the PO offers next (list "Next step" column), or null when nothing is left. */
    public static String nextStep(PoStatus status) {
        return switch (status) {
            case DRAFT -> "Approve";
            case APPROVED -> "Mark as sent";
            case SENT -> "Receive goods";
            case PARTLY_RECEIVED -> "Receive the rest";
            case RECEIVED, CANCELLED -> null;
        };
    }

    public static boolean canCancel(PoStatus status) {
        return canMove(status, PoStatus.CANCELLED);
    }

    /** Goods can be received only against a sent PO (or one partly received). */
    public static boolean canReceive(PoStatus status) {
        return status == PoStatus.SENT || status == PoStatus.PARTLY_RECEIVED;
    }

    /**
     * Lowest-price flags for one item across the quotations (null = not quoted). Only when at least two prices are given –
     * information only, the system does not choose. Equal lowest prices are all flagged.
     */
    public static boolean[] lowest(List<BigDecimal> prices) {
        boolean[] flags = new boolean[prices.size()];
        List<BigDecimal> given = prices.stream().filter(p -> p != null).toList();
        if (given.size() < 2) {
            return flags;
        }
        BigDecimal min = given.stream().min(BigDecimal::compareTo).orElseThrow();
        for (int i = 0; i < flags.length; i++) {
            flags[i] = prices.get(i) != null && prices.get(i).compareTo(min) == 0;
        }
        return flags;
    }

    /**
     * Checks one receipt line; returns the message, or null when it is fine. Received ≤ still due, damaged ≤ received,
     * nothing negative.
     */
    public static String receiptLineProblem(String itemName, int stillDue, int received, int damaged) {
        if (received < 0 || damaged < 0) {
            return "Quantities cannot be negative.";
        }
        if (received > stillDue) {
            return "Received for " + itemName + " is more than the " + stillDue + " still due.";
        }
        if (damaged > received) {
            return "Damaged cannot be more than received for " + itemName + ".";
        }
        return null;
    }

    /** Received when every line has all its quantity in (damaged ones included), otherwise partly received. */
    public static PoStatus afterReceipt(List<int[]> orderedAndReceived) {
        boolean full = orderedAndReceived.stream().allMatch(l -> l[1] >= l[0]);
        return full ? PoStatus.RECEIVED : PoStatus.PARTLY_RECEIVED;
    }

    /** A reference (cheque number or bank reference) is required unless the payment is in cash. */
    public static boolean referenceMissing(PayMethod method, String reference) {
        return method != PayMethod.CASH && (reference == null || reference.isBlank());
    }
}
