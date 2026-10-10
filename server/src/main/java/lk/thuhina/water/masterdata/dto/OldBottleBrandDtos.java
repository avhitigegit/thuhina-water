package lk.thuhina.water.masterdata.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Accepted old bottles tab (FR-06, BR-02). */
public final class OldBottleBrandDtos {

    private OldBottleBrandDtos() {
    }

    /** {@code takenIn} comes from sales (M06) – null until then. */
    public record OldBottleBrandResponse(
            long id,
            String name,
            String bottleTypeCode,
            String bottleTypeName,
            @Schema(nullable = true) String note,
            boolean active,
            LocalDate addedOn,
            @Schema(nullable = true) Integer takenIn,
            long version) {
    }

    public record CreateOldBottleBrandRequest(
            @NotBlank(message = "Enter the brand name.") @Size(max = 60, message = "Brand: at most 60 characters.") String name,
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            @Size(max = 200, message = "Note: at most 200 characters.") String note) {
    }

    /** Stop accepting / Accept again, or change the note or bottle type. */
    public record UpdateOldBottleBrandRequest(
            @NotBlank(message = "Choose the bottle type.") String bottleTypeCode,
            @Size(max = 200, message = "Note: at most 200 characters.") String note,
            boolean active,
            Long version) {
    }
}
