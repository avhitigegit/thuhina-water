package lk.thuhina.water.masterdata.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Other products tab (FR-02). */
public final class ProductDtos {

    public static final String MSG_REQUIRED = "Name and selling price are required.";

    private ProductDtos() {
    }

    public record ProductResponse(
            long id,
            String code,
            String name,
            BigDecimal sellingPrice,
            @Schema(nullable = true) BigDecimal costPrice,
            int stockQty,
            boolean active,
            long version) {
    }

    /** {@code openingStock} only here – later stock changes come from receipts, sales and adjustments. */
    public record CreateProductRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 100, message = "Name: at most 100 characters.") String name,
            @NotNull(message = MSG_REQUIRED) BigDecimal sellingPrice,
            BigDecimal costPrice,
            Integer openingStock,
            Boolean active) {
    }

    public record UpdateProductRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 100, message = "Name: at most 100 characters.") String name,
            @NotNull(message = MSG_REQUIRED) BigDecimal sellingPrice,
            BigDecimal costPrice,
            boolean active,
            Long version) {
    }
}
