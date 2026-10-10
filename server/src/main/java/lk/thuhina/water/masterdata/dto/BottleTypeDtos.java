package lk.thuhina.water.masterdata.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bottle types tab (FR-01). */
public final class BottleTypeDtos {

    public static final String MSG_REQUIRED = "Name and size in litres are required.";

    private BottleTypeDtos() {
    }

    /** {@code deposit} = deposit in force today; {@code inCirculation} comes with stock (M04) – null until then. */
    public record BottleTypeResponse(
            String code,
            String name,
            BigDecimal litres,
            boolean active,
            @Schema(nullable = true) BigDecimal deposit,
            @Schema(nullable = true) Integer inCirculation,
            long version) {
    }

    public record CreateBottleTypeRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 60, message = "Name: at most 60 characters.") String name,
            @NotNull(message = MSG_REQUIRED) BigDecimal litres,
            Boolean active) {
    }

    /** The size cannot be changed after creation. */
    public record UpdateBottleTypeRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 60, message = "Name: at most 60 characters.") String name,
            boolean active,
            Long version) {
    }
}
