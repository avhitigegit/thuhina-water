package lk.thuhina.water.customers.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.sales.dto.SalesDtos.TxnDetail;

/** Customers page (FR-08 – FR-13) and agreed prices (FR-05). */
public final class CustomerDtos {

    public static final String MSG_REQUIRED = "Name, address, phone, customer type and area are required.";

    private CustomerDtos() {
    }

    /**
     * New or changed customer. {@code deliveryDay} 1 = Monday … 7 = Sunday; {@code usualQty} per bottle type code.
     * Create only: {@code registeredOn} (default today), {@code agreedPrices} per bottle type with {@code agreedReason}
     * (from the registration date), {@code bottlesGiven} per bottle type on the registration date (first purchases,
     * M06) with {@code oldBottles} of brand {@code oldBrandId} (default: the first accepted brand), {@code paid} in cash
     * and {@code approveOverLimit} (Admin); {@code fromQuotationId} = registered from an accepted customer quotation (M16 –
     * the customer is linked to it and its agreed prices keep the link). Edit only: {@code version}. {@code allowDuplicatePhone} = "Save anyway".
     */
    public record CustomerRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 120, message = "Name: at most 120 characters.") String name,
            @NotBlank(message = MSG_REQUIRED) @Size(max = 250, message = "Address: at most 250 characters.") String address,
            @NotNull(message = MSG_REQUIRED) Long areaId,
            @NotBlank(message = MSG_REQUIRED) String phone,
            @NotNull(message = MSG_REQUIRED) Long customerTypeId,
            @NotNull(message = "Choose the delivery day.") Integer deliveryDay,
            @NotNull(message = "Choose the cycle.") Integer cycleWeeks,
            @NotNull(message = "Enter the next delivery date.") LocalDate nextDeliveryDate,
            Map<String, Integer> usualQty,
            @NotNull(message = "Choose the payment type.") PayType payType,
            BigDecimal creditLimit,
            Integer termsDays,
            @Size(max = 500, message = "Notes: at most 500 characters.") String notes,
            Boolean whatsappOptIn,
            String whatsappNumber,
            LocalDate registeredOn,
            Map<String, BigDecimal> agreedPrices,
            @Size(max = 250, message = "Reason: at most 250 characters.") String agreedReason,
            Boolean allowDuplicatePhone,
            Long version,
            Map<String, Integer> bottlesGiven,
            Integer oldBottles,
            Long oldBrandId,
            BigDecimal paid,
            Boolean approveOverLimit,
            Long fromQuotationId) {
    }

    /** Bottles of one type with the customer: held, empties to collect, owed. */
    public record BottleBalance(String bottleTypeCode, String label, int held, int toCollect, int owed) {
    }

    public record CustomerListRow(
            long id,
            String code,
            String name,
            String address,
            String areaName,
            String phone,
            String typeName,
            int deliveryDay,
            int cycleWeeks,
            LocalDate nextDeliveryDate,
            String payType,
            BigDecimal creditLimit,
            String status,
            @Schema(nullable = true) LocalDate statusDate,
            @Schema(nullable = true) String statusReason,
            List<BottleBalance> bottles,
            int toCollect,
            int owed,
            BigDecimal balance,
            boolean overLimit,
            @Schema(nullable = true, description = "e.g. \"20L Rs. 270.00\" when the customer has an agreed price today") String agreed) {
    }

    /** A page of customers plus the count line ("39 of 40 customers (38 active, 2 inactive)"). */
    public record CustomerListResponse(List<CustomerListRow> items, int page, int size, long total, long all, long active,
                                       long inactive) {
    }

    public record LookupRow(long id, String code, String name, String phone, String areaName, String status) {
    }

    /** The next agreed-price change after today for a bottle type (shown under the price). */
    public record ScheduledPrice(BigDecimal price, LocalDate from) {
    }

    /**
     * Water price of one bottle type for this customer today: the price, whether it is agreed, the standard price, the
     * next scheduled agreed change, bottles a month at the usual quantity, and the main factory's filling charge (guide).
     */
    public record WaterPriceView(
            String bottleTypeCode,
            String label,
            @Schema(nullable = true) BigDecimal price,
            boolean agreed,
            @Schema(nullable = true) BigDecimal standard,
            @Schema(nullable = true) ScheduledPrice next,
            int bottlesPerMonth,
            @Schema(nullable = true) BigDecimal fillingCharge) {
    }

    /** Everything the side panel and the edit pop-up need. */
    public record CustomerDetail(
            long id,
            String code,
            String name,
            String address,
            long areaId,
            String areaName,
            String phone,
            long customerTypeId,
            String typeName,
            int deliveryDay,
            int cycleWeeks,
            LocalDate nextDeliveryDate,
            Map<String, Integer> usualQty,
            String payType,
            BigDecimal creditLimit,
            int termsDays,
            LocalDate registeredOn,
            String status,
            @Schema(nullable = true) LocalDate statusDate,
            @Schema(nullable = true) String statusReason,
            @Schema(nullable = true) String notes,
            boolean whatsappOptIn,
            @Schema(nullable = true) String whatsappNumber,
            List<BottleBalance> bottles,
            BigDecimal outstanding,
            boolean overLimit,
            boolean overdue,
            List<WaterPriceView> prices,
            long version) {
    }

    /**
     * Result of a save: the customer and, for a new one, how many agreed prices were set and the first-purchase bills
     * of the bottles given on the registration date (for "Print bill").
     */
    public record CustomerSaved(CustomerDetail customer, int agreedPricesSet, List<TxnDetail> bills) {
    }

    /**
     * One line of the History tab: registration, changes, prices (kind REGISTERED / CHANGED / PRICE) and bills
     * (kind SALE, with the bill, amount, paid and the running account balance after it) and payments (kind PAYMENT, billNo =
     * the receipt number, paid = the amount, with the running balance after it).
     */
    public record HistoryRow(Instant at, LocalDate date, String kind, String description, String by,
                             @Schema(nullable = true) String byName,
                             @Schema(nullable = true) Long txnId,
                             @Schema(nullable = true) String billNo,
                             @Schema(nullable = true) BigDecimal amount,
                             @Schema(nullable = true) BigDecimal paid,
                             @Schema(nullable = true) BigDecimal balance) {
    }

    public record DeactivateRequest(
            @NotNull(message = "Enter the inactive-from date.") LocalDate date,
            @NotBlank(message = "Enter the reason for deactivating.")
            @Size(max = 250, message = "Reason: at most 250 characters.") String reason) {
    }

    // ------------------------------------------------------------------ agreed prices

    public record CustomerPriceRow(
            long id,
            String cpNo,
            long customerId,
            String customerCode,
            String customerName,
            String customerTypeName,
            String bottleTypeCode,
            String label,
            BigDecimal price,
            @Schema(nullable = true) BigDecimal standardAtSet,
            @Schema(nullable = true) BigDecimal standardNow,
            LocalDate validFrom,
            @Schema(nullable = true) LocalDate validUntil,
            String reason,
            @Schema(nullable = true) Long quotationId,
            String state,
            @Schema(nullable = true) String cancelReason,
            String createdBy,
            @Schema(nullable = true) String createdByName,
            int bottlesPerMonth) {
    }

    /** Agreed price from a date (may be in the future), optionally until a date. */
    public record SetCustomerPriceRequest(
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            @NotNull(message = "Enter the agreed price per bottle.") BigDecimal price,
            @NotNull(message = "Enter the date the agreed price starts.") LocalDate from,
            LocalDate until,
            @NotBlank(message = "Enter the reason for the agreed price (e.g. about 3,500 bottles a month).")
            @Size(max = 250, message = "Reason: at most 250 characters.") String reason,
            Long quotationId) {
    }

    /** Back to the standard price list from a date. */
    public record EndCustomerPriceRequest(
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            @NotNull(message = "Enter the date the standard price starts again.") LocalDate from,
            @NotBlank(message = "Enter the reason.") @Size(max = 250, message = "Reason: at most 250 characters.") String reason) {
    }

    public record WaterPriceResponse(BigDecimal price, boolean agreed, @Schema(nullable = true) BigDecimal standard) {
    }
}
