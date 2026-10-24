package lk.thuhina.water.quotations.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Customer quotations (design 6 Customer quotations, 7.2): list, items with today's prices, new / edit / copy, steps, prices. */
public final class CustomerQuotationDtos {

    private CustomerQuotationDtos() {
    }

    // ------------------------------------------------------------------ items that can be quoted

    /**
     * Something that can be quoted: water or deposit per active bottle type, an active product, or free text (OTHER). {@code key}
     * "WATER|B20", "DEPOSIT|B20", "PRODUCT|P01", "OTHER"; {@code currentPrice} = the customer's price now (agreed or standard) or
     * the standard price for the chosen customer type; {@code agreed} = the customer has an agreed price for it.
     */
    public record CqItemOption(String key, String kind, @Schema(nullable = true) String bottleTypeCode,
                               @Schema(nullable = true) String productCode, String label, String description, String unit,
                               @Schema(nullable = true) BigDecimal currentPrice, boolean agreed) {
    }

    // ------------------------------------------------------------------ new / edit

    /** A line: {@code kind} WATER / DEPOSIT (with the bottle type), PRODUCT (with the product code) or OTHER. */
    public record CqLineRequest(
            String kind,
            String bottleTypeCode,
            String productCode,
            @Size(max = 200, message = "Description: at most 200 characters.") String description,
            Integer qty,
            @Size(max = 30, message = "Unit: at most 30 characters.") String unit,
            BigDecimal unitPrice) {
    }

    /**
     * New, changed or copied draft. To an existing customer ({@code customerId}) or a new organisation ({@code orgName} and the
     * contact details). {@code copyOf} on a new draft made with "Make a copy"; {@code version} on an edit.
     */
    public record CqRequest(
            Long customerId,
            @Size(max = 150, message = "Organisation: at most 150 characters.") String orgName,
            @Size(max = 100, message = "Contact person: at most 100 characters.") String contact,
            @Size(max = 100, message = "Designation: at most 100 characters.") String designation,
            @Size(max = 250, message = "Address: at most 250 characters.") String address,
            Long areaId,
            @Size(max = 20, message = "Phone: at most 20 characters.") String phone,
            @Size(max = 100, message = "Email: at most 100 characters.") String email,
            Long customerTypeId,
            LocalDate quoteDate,
            LocalDate validUntil,
            @Size(max = 200, message = "Subject: at most 200 characters.") String subject,
            @Size(max = 200, message = "Delivery: at most 200 characters.") String deliveryText,
            Integer termsDays,
            @Size(max = 4000, message = "Terms and conditions: at most 4000 characters.") String conditions,
            @Valid List<CqLineRequest> lines,
            Long copyOf,
            Long version) {
    }

    // ------------------------------------------------------------------ list

    /**
     * A row of the list. {@code shown} = DRAFT / SENT / EXPIRED / ACCEPTED / REJECTED with {@code shownLabel} ("Not accepted",
     * "Expired"); {@code nextStep} as in the prototype.
     */
    public record CqListRow(
            long id,
            String cqNo,
            LocalDate quoteDate,
            String toName,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            @Schema(nullable = true) String contact,
            @Schema(nullable = true) String designation,
            @Schema(nullable = true) String subject,
            int lineCount,
            String customerTypeName,
            BigDecimal total,
            LocalDate validUntil,
            String status,
            String shown,
            String shownLabel,
            @Schema(nullable = true) String sentViaText,
            String nextStep) {
    }

    /** Page KPIs: waiting for an answer (count and value), drafts, accepted (count and value), expired or not accepted. */
    public record CqKpis(int waiting, BigDecimal waitingTotal, int drafts, int accepted, BigDecimal acceptedTotal, int expiredOrRejected) {
    }

    public record CqListResponse(List<CqListRow> items, CqKpis kpis) {
    }

    // ------------------------------------------------------------------ one quotation

    /** A line with its amount and the price when quoted; {@code belowCurrent} = quoted below that price. */
    public record CqLineView(int lineNo, String kind, @Schema(nullable = true) String bottleTypeCode,
                             @Schema(nullable = true) String productCode, String description, int qty, @Schema(nullable = true) String unit,
                             BigDecimal unitPrice, BigDecimal amount, @Schema(nullable = true) BigDecimal currentPrice,
                             boolean currentAgreed, boolean belowCurrent) {
    }

    /** A step of the history: Draft (or "Copy of …"), Edited, Sent (by email), Accepted, Not accepted (reason), …. */
    public record CqHistoryRow(Instant at, String event, String label, @Schema(nullable = true) String note, String byName) {
    }

    /** A quoted water price for the New customer pre-fill: only those that differ from the standard price of the type. */
    public record CqWaterPrice(String bottleTypeCode, String label, BigDecimal price, @Schema(nullable = true) BigDecimal standard) {
    }

    /**
     * The view pop-up. {@code can…} = the buttons the next step allows (the role is checked separately). {@code emailSubject} /
     * {@code emailBody} for the Email button (mailto:). {@code agreedPrices} and {@code registerNotes} pre-fill Register as customer.
     */
    public record CqView(
            long id,
            String cqNo,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            String orgName,
            @Schema(nullable = true) String contact,
            @Schema(nullable = true) String designation,
            @Schema(nullable = true) String address,
            @Schema(nullable = true) Long areaId,
            @Schema(nullable = true) String areaName,
            @Schema(nullable = true) String phone,
            @Schema(nullable = true) String email,
            long customerTypeId,
            String customerTypeName,
            LocalDate quoteDate,
            LocalDate validUntil,
            @Schema(nullable = true) String subject,
            @Schema(nullable = true) String deliveryText,
            int termsDays,
            String termsLabel,
            @Schema(nullable = true) String conditions,
            String status,
            String shown,
            String shownLabel,
            @Schema(nullable = true) String sentVia,
            @Schema(nullable = true) String sentViaText,
            @Schema(nullable = true) LocalDate sentOn,
            @Schema(nullable = true) LocalDate acceptedOn,
            @Schema(nullable = true) String rejectReason,
            @Schema(nullable = true) LocalDate pricesAppliedOn,
            @Schema(nullable = true) String copyOfNo,
            List<CqLineView> lines,
            BigDecimal total,
            List<CqHistoryRow> history,
            boolean hasWater,
            boolean canEdit,
            boolean canSend,
            boolean canAnswer,
            boolean canRegister,
            boolean canApplyPrices,
            String emailSubject,
            String emailBody,
            List<CqWaterPrice> agreedPrices,
            String registerNotes,
            String createdByName,
            long version) {
    }

    /** After a save or a step: the quotation and the message to show. */
    public record CqSaved(CqView quotation, String message) {
    }

    // ------------------------------------------------------------------ steps

    /** Mark as sent: {@code via} EMAIL / BY_HAND / POST / WHATSAPP; {@code date} default today. */
    public record CqSendRequest(String via, LocalDate date) {
    }

    public record CqRejectRequest(@Size(max = 250, message = "Reason: at most 250 characters.") String reason) {
    }

    /** Accepted on {@code date} (default today). */
    public record CqAcceptRequest(LocalDate date) {
    }

    /** The quoted water prices become the customer's agreed prices from {@code from} (default today). */
    public record CqApplyRequest(LocalDate from) {
    }
}
