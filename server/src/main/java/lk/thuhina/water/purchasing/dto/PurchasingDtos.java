package lk.thuhina.water.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.PayMethod;

/** Purchasing (design 6 Purchasing, 7.2): quotation requests, quotations, purchase orders, goods receipts, supplier payments. */
public final class PurchasingDtos {

    private PurchasingDtos() {
    }

    // ------------------------------------------------------------------ shared

    /** An item with a quantity; {@code type} BOTTLE (empty bottles) or PRODUCT, {@code name} as shown ("Empty 20L Bottle"). */
    public record ItemQty(String itemCode, String itemType, String name, int qty) {
    }

    public record SupplierLink(long id, String code, String name) {
    }

    public record PoLink(long id, String poNo, String status, String statusLabel) {
    }

    // ------------------------------------------------------------------ quotation requests (FR-15)

    public record QrItemInput(String itemCode, Integer qty) {
    }

    /** New request: date ≤ today, needed by (optional), note; items with qty > 0; the suppliers asked to quote. */
    public record QrRequest(
            @NotNull(message = "Enter the date.") LocalDate date,
            LocalDate requiredBy,
            @Size(max = 300, message = "Note: at most 300 characters.") String note,
            List<QrItemInput> items,
            List<Long> supplierIds) {
    }

    /** Quotations tab row: "n of m suppliers", the chosen supplier and the active PO. */
    public record QrRow(
            long id,
            String qrNo,
            LocalDate date,
            @Schema(nullable = true) LocalDate requiredBy,
            @Schema(nullable = true) String note,
            String status,
            String statusLabel,
            List<ItemQty> items,
            int suppliersAsked,
            int quotations,
            @Schema(nullable = true) SupplierLink chosen,
            @Schema(nullable = true) PoLink po) {
    }

    public record AskedSupplier(long id, String code, String name, boolean quoted) {
    }

    /** One quoted price in the comparison; {@code lowest} only when two or more suppliers quoted the item. */
    public record QuotePrice(String itemCode, @Schema(nullable = true) BigDecimal unitPrice, @Schema(nullable = true) BigDecimal lineTotal,
                             boolean lowest) {
    }

    /**
     * A quotation as a comparison column (FR-17). {@code complete} = every requested item has a price. {@code attachmentFile} =
     * the scanned file can be opened at {@code GET /quotations/{id}/attachment} (seed quotations have only a file name).
     */
    public record QuoteColumn(
            long id,
            String quoteNo,
            SupplierLink supplier,
            @Schema(nullable = true) String supplierRef,
            LocalDate date,
            LocalDate validUntil,
            boolean expired,
            int deliveryDays,
            @Schema(nullable = true) String notes,
            @Schema(nullable = true) String attachmentName,
            boolean attachmentFile,
            List<QuotePrice> prices,
            BigDecimal total,
            boolean complete,
            boolean selected,
            @Schema(nullable = true) String selectionReason,
            @Schema(nullable = true) String selectedByName,
            @Schema(nullable = true) LocalDate selectedOn) {
    }

    /**
     * Request detail with the side-by-side comparison. {@code canAddQuotation} = still open and a supplier has not quoted;
     * {@code canChoose} = open with quotations; {@code canCreatePo} = a quotation is chosen and has no active PO.
     */
    public record QrDetail(
            long id,
            String qrNo,
            LocalDate date,
            @Schema(nullable = true) LocalDate requiredBy,
            @Schema(nullable = true) String note,
            String status,
            String statusLabel,
            String createdByName,
            List<ItemQty> items,
            List<AskedSupplier> suppliers,
            List<QuoteColumn> quotations,
            @Schema(nullable = true) PoLink po,
            boolean canAddQuotation,
            boolean canChoose,
            boolean canCreatePo,
            long version) {
    }

    // ------------------------------------------------------------------ quotations (FR-16, FR-17)

    /** A unit price per requested item; null or blank = not quoted. */
    public record PriceInput(String itemCode, BigDecimal unitPrice) {
    }

    public record QuotationRequestBody(
            Long supplierId,
            @Size(max = 50, message = "Supplier reference: at most 50 characters.") String supplierRef,
            @NotNull(message = "Enter the date.") LocalDate date,
            LocalDate validUntil,
            Integer deliveryDays,
            List<PriceInput> prices,
            @Size(max = 300, message = "Notes: at most 300 characters.") String notes) {
    }

    public record ReasonRequest(@Size(max = 300, message = "Reason: at most 300 characters.") String reason) {
    }

    // ------------------------------------------------------------------ purchase orders (FR-18, FR-19)

    public record PoLineInput(String itemCode, Integer qty, BigDecimal unitPrice) {
    }

    /** Draft changes: lines (qty 0 removes the line), expected delivery date, notes. */
    public record PoUpdateRequest(
            @NotNull(message = "Enter the expected delivery date.") LocalDate expectedDate,
            @Size(max = 300, message = "Notes: at most 300 characters.") String notes,
            List<PoLineInput> lines,
            Long version) {
    }

    /** Purchase orders tab row: received / ordered quantities and the next step. */
    public record PoRow(
            long id,
            String poNo,
            LocalDate date,
            LocalDate expectedDate,
            SupplierLink supplier,
            String qrNo,
            List<ItemQty> items,
            BigDecimal total,
            int received,
            int ordered,
            String status,
            String statusLabel,
            @Schema(nullable = true) String nextStep) {
    }

    public record SupplierInfo(long id, String code, String name, @Schema(nullable = true) String contact, String phone,
                               @Schema(nullable = true) String address, int termsDays, String termsLabel) {
    }

    public record PoLineView(long id, String itemType, String itemCode, String name, int qty, BigDecimal unitPrice, BigDecimal amount,
                             int receivedQty, int damagedQty, int stillDue) {
    }

    public record PoHistoryRow(String status, String statusLabel, Instant ts, String username, String userName,
                             @Schema(nullable = true) String note) {
    }

    public record ReceiptLineView(String itemCode, String name, int received, int damaged, int good, BigDecimal unitPrice, BigDecimal value) {
    }

    public record ReceiptView(long id, String grnNo, LocalDate date, @Schema(nullable = true) String deliveryNote,
                              @Schema(nullable = true) String note, BigDecimal value, String createdByName, List<ReceiptLineView> lines) {
    }

    /**
     * PO pop-up: step bar status, summary, lines, history, receipts. {@code editable} = Draft (lines, expected date, notes);
     * {@code canCancel} = Draft / Approved; {@code canReceive} = Sent / Partly received.
     */
    public record PoDetail(
            long id,
            String poNo,
            LocalDate date,
            LocalDate expectedDate,
            String status,
            String statusLabel,
            @Schema(nullable = true) String nextStep,
            SupplierInfo supplier,
            long qrId,
            String qrNo,
            long quotationId,
            String quoteNo,
            @Schema(nullable = true) String supplierRef,
            @Schema(nullable = true) String notes,
            List<PoLineView> lines,
            BigDecimal total,
            BigDecimal receivedValue,
            String createdByName,
            @Schema(nullable = true) String approvedByName,
            @Schema(nullable = true) LocalDate approvedOn,
            @Schema(nullable = true) LocalDate sentOn,
            @Schema(nullable = true) String cancelReason,
            List<PoHistoryRow> history,
            List<ReceiptView> receipts,
            boolean editable,
            boolean canCancel,
            boolean canReceive,
            long version) {
    }

    // ------------------------------------------------------------------ goods receipt (FR-20)

    public record ReceiptLineInput(Long poLineId, Integer received, Integer damaged) {
    }

    public record ReceiptRequest(
            @NotNull(message = "Enter the date received.") LocalDate date,
            @Size(max = 50, message = "Delivery note: at most 50 characters.") String deliveryNote,
            @Size(max = 300, message = "Note: at most 300 characters.") String note,
            List<ReceiptLineInput> lines) {
    }

    /** The saved receipt and the PO after it (new status, received quantities). */
    public record ReceiptResult(ReceiptView receipt, PoDetail po) {
    }

    // ------------------------------------------------------------------ supplier balances & payments (FR-21)

    /**
     * Owed = value of good items received − payments. {@code oldestUnpaidDate} = the oldest receipt not yet covered by the
     * payments (paid oldest first); {@code overdue} when it is older than the supplier's terms.
     */
    public record SupplierBalance(
            long supplierId,
            String code,
            String name,
            int termsDays,
            String termsLabel,
            BigDecimal received,
            BigDecimal paid,
            BigDecimal owed,
            @Schema(nullable = true) LocalDate oldestUnpaidDate,
            @Schema(nullable = true) Integer oldestUnpaidDays,
            boolean overdue) {
    }

    public record PaymentRequest(
            Long supplierId,
            @NotNull(message = "Enter the date.") LocalDate date,
            BigDecimal amount,
            PayMethod method,
            @Size(max = 100, message = "Reference: at most 100 characters.") String reference,
            Long poId,
            @Size(max = 300, message = "Note: at most 300 characters.") String note) {
    }

    public record PaymentRow(
            long id,
            String spNo,
            LocalDate date,
            SupplierLink supplier,
            @Schema(nullable = true) Long poId,
            @Schema(nullable = true) String poNo,
            String method,
            String methodLabel,
            @Schema(nullable = true) String reference,
            BigDecimal amount,
            @Schema(nullable = true) String note,
            String createdByName) {
    }

    /** Quotation attachment upload result. */
    public record AttachmentResponse(long quotationId, String attachmentName) {
    }
}
