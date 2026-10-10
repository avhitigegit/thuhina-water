package lk.thuhina.water.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.inventory.model.DamageLocation;
import lk.thuhina.water.inventory.model.StockAdjustment;

/** Stock page (FR-27 – FR-31): stock by status, damage, adjustments, minimum levels, movements. */
public final class StockDtos {

    private StockDtos() {
    }

    /** One bottle type: the five buckets, in circulation (all but written off), the minimum filled level. */
    public record BottleStock(
            String code,
            String name,
            String label,
            int empty,
            int factory,
            int filled,
            int customers,
            int writtenOff,
            int inCirculation,
            @Schema(nullable = true) Integer minFilled,
            boolean low) {
    }

    /** Product stock with its cost value (stock × cost price). */
    public record ProductStock(long id, String code, String name, int stockQty, @Schema(nullable = true) BigDecimal costPrice,
                               BigDecimal costValue, boolean active) {
    }

    /** Low-stock alert (FR-31): filled bottles in store below the minimum. */
    public record LowStockAlert(String bottleTypeCode, String name, int filled, int minFilled, int atFactory, String message) {
    }

    public record StockOverview(List<BottleStock> bottles, List<ProductStock> products, List<LowStockAlert> alerts) {
    }

    /**
     * A posting for one item, as on the Movements tab: change per bucket (bottles) or of product stock.
     */
    public record MovementRow(
            LocalDate date,
            String sourceType,
            @Schema(nullable = true) String docNo,
            String description,
            String itemCode,
            String itemName,
            int empty,
            int factory,
            int filled,
            int customers,
            int writtenOff,
            int product,
            String createdBy,
            @Schema(nullable = true) String createdByName,
            Instant createdAt) {
    }

    /** A row of the Damage &amp; adjustments tab: kind DAMAGED / LOST / COUNT. */
    public record DamageListRow(
            String ref,
            LocalDate date,
            String kind,
            String itemCode,
            String itemName,
            int qty,
            @Schema(nullable = true) String responsibility,
            @Schema(nullable = true) String where,
            @Schema(nullable = true) String customerName,
            String reason,
            String createdBy,
            @Schema(nullable = true) String createdByName,
            boolean reversed) {
    }

    /** Company damage (FR-29): bottles at a place are written off at company cost. */
    public record CompanyDamageRequest(
            @NotNull(message = "Enter the date.") LocalDate date,
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            @NotNull(message = "Enter the number of damaged bottles.") Integer qty,
            @NotNull(message = "Select where the damage happened.") DamageLocation location,
            @NotBlank(message = "Enter the reason.") @Size(max = 200, message = "Reason: at most 200 characters.") String reason,
            @Size(max = 300, message = "Note: at most 300 characters.") String note) {
    }

    /**
     * Stock adjustment (FR-30). Bottles: {@code bucket} EMPTY / FILLED / FACTORY and mode COUNT (with {@code counted})
     * or LOST (with {@code qty}). Products: mode COUNT with {@code counted}; no bucket.
     */
    public record AdjustmentRequest(
            @NotNull(message = "Enter the date.") LocalDate date,
            @NotBlank(message = "Choose the item.") String itemCode,
            @NotNull(message = "Choose physical count or lost bottles.") StockAdjustment.Mode mode,
            String bucket,
            Integer counted,
            Integer qty,
            @NotBlank(message = "Enter the reason for the adjustment.")
            @Size(max = 200, message = "Reason: at most 200 characters.") String reason) {
    }

    public record AdjustmentResponse(String adjNo, String itemCode, String bucket, String mode, int systemQty,
                                     @Schema(nullable = true) Integer countedQty, int delta, String description) {
    }

    public record MinLevelRequest(@NotNull(message = "Enter the minimum filled level.") Integer minFilled) {
    }
}
