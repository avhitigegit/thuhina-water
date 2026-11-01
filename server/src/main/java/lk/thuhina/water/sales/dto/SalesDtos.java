package lk.thuhina.water.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.DamageMode;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.model.SettleMethod;
import lk.thuhina.water.sales.rules.Problem;
import lk.thuhina.water.sales.rules.SaleLine;

/**
 * Sales &amp; Bills (FR-32 – FR-41, FR-59). Every sale request has the customer, the date (not after today), an optional
 * paper bill number (required on delivery bills), money paid on the bill, a note, and {@code approveOverLimit} for the
 * Admin's credit-limit approval. The same request body is sent to {@code …/preview} (nothing is saved; missing numbers
 * count as 0) to get the live calculation.
 */
public final class SalesDtos {

    public static final String MSG_CUSTOMER = "Select a customer.";
    public static final String MSG_DATE = "Enter a valid date (DD/MM/YYYY).";
    public static final String MSG_BOTTLE = "Choose the bottle type.";
    private static final String MSG_NOTE = "Note: at most 500 characters.";

    private SalesDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record BillLineRequest(String bottleTypeCode, Integer filled, Integer empties) {
    }

    /** Exchange or Left at door from a paper bill (the daily grid in M07 sends one per customer). */
    public record BillRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @Schema(description = "EXCHANGE (default) or LEFT_AT_DOOR") SalesKind kind,
            List<BillLineRequest> lines,
            BigDecimal paid,
            @Schema(description = "How the money paid was received: CASH (default), BANK_TRANSFER or CHEQUE") PayMethod payMethod,
            @Schema(description = "Cheque number or bank transfer reference (needed for those methods)")
            @Size(max = 100, message = "Reference: at most 100 characters.") String payReference,
            @Size(max = 500, message = MSG_NOTE) String note,
            Boolean phoneConfirmed,
            Boolean approveOverLimit) {
    }

    /** First purchase of a bottle type: deposit + water; accepted old bottles waive the deposit. */
    public record FirstPurchaseRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @NotBlank(message = MSG_BOTTLE) String bottleTypeCode,
            Integer qty,
            Integer oldBottles,
            Long oldBrandId,
            BigDecimal paid,
            @Size(max = 500, message = MSG_NOTE) String note,
            Boolean approveOverLimit) {
    }

    /** Extra filled bottles; bottles beyond the empties returned need a deposit (or an old bottle). */
    public record ExtraOrderRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @NotBlank(message = MSG_BOTTLE) String bottleTypeCode,
            Integer filled,
            Integer empties,
            Integer oldBottles,
            Long oldBrandId,
            BigDecimal paid,
            @Size(max = 500, message = MSG_NOTE) String note,
            Boolean approveOverLimit) {
    }

    /** Empties left at the door collected on a later trip. */
    public record CollectEmptiesRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @NotBlank(message = MSG_BOTTLE) String bottleTypeCode,
            Integer qty,
            @Size(max = 500, message = MSG_NOTE) String note) {
    }

    /** Customer-damaged bottle: REPLACE (default; {@code giveFilled} default true) or REDUCE the holding. */
    public record CustomerDamageRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @NotBlank(message = MSG_BOTTLE) String bottleTypeCode,
            Integer qty,
            DamageMode mode,
            Boolean giveFilled,
            @Size(max = 200, message = "Reason: at most 200 characters.") String reason,
            @Size(max = 300, message = "Note: at most 300 characters.") String note,
            BigDecimal paid,
            Boolean approveOverLimit) {
    }

    /** Settle bottles owed: RETURN (default) the bottles or pay the DEPOSIT and keep them. */
    public record SettleOwedRequest(
            @NotNull(message = MSG_CUSTOMER) Long customerId,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            @NotBlank(message = MSG_BOTTLE) String bottleTypeCode,
            Integer qty,
            SettleMethod method,
            BigDecimal paid,
            @Size(max = 500, message = MSG_NOTE) String note,
            Boolean approveOverLimit) {
    }

    /** A product on a product sale; {@code unitPrice} empty = the selling price. */
    public record ProductItemRequest(String productCode, Integer qty, BigDecimal unitPrice) {
    }

    /** Product sale to a customer or a walk-in ({@code walkInName}); paid by Cash (default), Bank transfer or Cheque. */
    public record ProductSaleRequest(
            Long customerId,
            @Size(max = 120, message = "Walk-in name: at most 120 characters.") String walkInName,
            @NotNull(message = MSG_DATE) LocalDate date,
            String paperBillNo,
            List<ProductItemRequest> items,
            BigDecimal paid,
            PayMethod payMethod,
            @Size(max = 500, message = MSG_NOTE) String note,
            Boolean approveOverLimit) {
    }

    public record ReverseRequest(
            @NotBlank(message = "Enter the reason for the reversal.")
            @Size(max = 300, message = "Reason: at most 300 characters.") String reason) {
    }

    // ------------------------------------------------------------------ previews

    /** A customer's bottles of one type after the bill, with the change ("after this bill" on the customer card). */
    public record BottlesAfter(String bottleTypeCode, String label, int held, int toCollect, int owed, int heldChange,
                               int toCollectChange, int owedChange) {
    }

    /** Credit check for the bill (BR-09): balance before / after, the limit and whether Admin approval is needed. */
    public record CreditView(BigDecimal balance, BigDecimal after, BigDecimal limit, boolean exceeds) {
    }

    /**
     * Live calculation: lines (with "(agreed price)" flags), amounts, the customer's bottles after the bill, the credit
     * check, problems that stop saving (empty = can be saved) and warnings that do not.
     */
    public record SalePreview(
            List<SaleLine> lines,
            BigDecimal water,
            BigDecimal deposit,
            BigDecimal product,
            BigDecimal amount,
            BigDecimal paid,
            List<BottlesAfter> after,
            @Schema(nullable = true) CreditView credit,
            List<Problem> problems,
            List<String> warnings) {
    }

    /**
     * Bottles given on the registration date (before saving): one first-purchase line per bottle type, totals, cash
     * paid, the credit check of the new customer and problems (e.g. "Only 5 filled 20L bottles in store.").
     */
    public record RegistrationPreview(
            List<SaleLine> lines,
            BigDecimal water,
            BigDecimal deposit,
            BigDecimal amount,
            BigDecimal paid,
            CreditView credit,
            List<Problem> problems) {
    }

    // ------------------------------------------------------------------ bills

    /** Next delivery date before and after the bill (design 5.4). */
    public record ScheduleChange(LocalDate before, LocalDate after) {
    }

    /** A bill-to-bill link: the reversal of this bill, or the bill this reversal cancels. */
    public record BillLink(long id, String billNo, LocalDate date, @Schema(nullable = true) String note) {
    }

    /** One ledger effect of the bill: e.g. "20L", "Filled in store", −3. */
    public record EffectView(String item, String place, int change) {
    }

    /** A row of the bills list. */
    public record TxnRow(
            long id,
            String billNo,
            @Schema(nullable = true) String paperBillNo,
            String kind,
            String kindLabel,
            LocalDate date,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            String customerName,
            boolean walkIn,
            int filled,
            int empties,
            BigDecimal amount,
            BigDecimal paid,
            boolean reversed,
            @Schema(nullable = true) String reversalOfBillNo,
            @Schema(description = "Saved by the delivery staff on the phone (My Deliveries)") boolean enteredOnPhone) {
    }

    /** Bill details: everything on the pop-up and the print. */
    public record TxnDetail(
            long id,
            String billNo,
            @Schema(nullable = true) String paperBillNo,
            String kind,
            String kindLabel,
            LocalDate date,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            String customerName,
            @Schema(nullable = true) String customerAddress,
            @Schema(nullable = true) String customerPhone,
            @Schema(nullable = true) String payType,
            List<SaleLine> lines,
            BigDecimal water,
            BigDecimal deposit,
            BigDecimal product,
            BigDecimal productCost,
            BigDecimal amount,
            BigDecimal paid,
            @Schema(nullable = true) String payMethod,
            @Schema(nullable = true) String payReference,
            boolean enteredOnPhone,
            @Schema(nullable = true) String note,
            boolean phoneConfirmed,
            @Schema(nullable = true) String damageMode,
            @Schema(nullable = true) String settleMethod,
            @Schema(nullable = true) String creditOverrideBy,
            @Schema(nullable = true) String creditOverrideByName,
            @Schema(nullable = true) BillLink reversalOf,
            @Schema(nullable = true) BillLink reversedBy,
            List<EffectView> effects,
            String createdBy,
            @Schema(nullable = true) String createdByName,
            Instant createdAt) {
    }

    /** Result of saving a sale: the bill and, for delivery bills and first purchases, the schedule change. */
    public record SaleResult(TxnDetail txn, @Schema(nullable = true) ScheduleChange schedule) {
    }

    public record TxnListResponse(List<TxnRow> items, int page, int size, long total) {
    }

    /** Enter Bills: the suggested next paper bill number (highest numeric one + 1) and the highest so far. */
    public record NextPaperBill(@Schema(nullable = true) String next, @Schema(nullable = true) String highest) {
    }

    /** "Bill 41275 is already entered – …" / "Bill 41275 has not been entered yet." */
    public record PaperBillCheck(String paperBillNo, boolean entered, @Schema(nullable = true) TxnRow txn, String message) {
    }
}
