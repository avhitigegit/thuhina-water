package lk.thuhina.water.masterdata.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.masterdata.model.PriceEntry;

/** Prices and deposits tab (FR-04, FR-07, BR-08). */
public final class PriceDtos {

    private PriceDtos() {
    }

    /** A price value and the day it starts. */
    public record PriceValue(long entryId, BigDecimal price, LocalDate effectiveFrom) {
    }

    /**
     * One cell of the water grid or the deposit table: the price in force on the date, the next scheduled one, and
     * whether the client confirmed it ("example" otherwise).
     */
    public record PriceCell(
            String key,
            String kind,
            String bottleTypeCode,
            @Schema(nullable = true) Long customerTypeId,
            @Schema(nullable = true) PriceValue current,
            @Schema(nullable = true) PriceValue next,
            boolean confirmed) {
    }

    public record PriceBottle(String code, String name) {
    }

    public record PriceCustomerType(long id, String name) {
    }

    /** Rows = active bottle types, columns = active customer types (prototype grid). */
    public record PriceMatrixResponse(
            LocalDate date,
            List<PriceBottle> bottleTypes,
            List<PriceCustomerType> customerTypes,
            List<PriceCell> water,
            List<PriceCell> deposits) {
    }

    /** A row of the Price history pop-up; {@code status} CURRENT / SCHEDULED / OLD as of today. */
    public record PriceHistoryRow(
            long id,
            String kind,
            String bottleTypeCode,
            @Schema(nullable = true) Long customerTypeId,
            String label,
            BigDecimal price,
            LocalDate effectiveFrom,
            String reason,
            String createdBy,
            @Schema(nullable = true) String createdByName,
            Instant createdAt,
            String status) {
    }

    /** A price change: a new entry from {@code effectiveFrom} (today or later). WATER needs a customer type, DEPOSIT none. */
    public record SetPriceRequest(
            @NotNull(message = "Choose the price to change.") PriceEntry.Kind kind,
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            Long customerTypeId,
            @NotNull(message = "Enter a price greater than zero.") BigDecimal price,
            @NotNull(message = "Enter the effective date.") LocalDate effectiveFrom,
            @NotBlank(message = "Enter the reason for the change.")
            @Size(max = 200, message = "Reason: at most 200 characters.") String reason) {
    }
}
