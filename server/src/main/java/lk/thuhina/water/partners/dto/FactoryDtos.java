package lk.thuhina.water.partners.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Filling factories tab (FR-22, BR-11). */
public final class FactoryDtos {

    private FactoryDtos() {
    }

    /** The charge in force today for one bottle type ({@code bottleLabel} "20L"). */
    public record FactoryChargeView(String bottleTypeCode, String bottleLabel, BigDecimal charge, LocalDate effectiveFrom) {
    }

    /** {@code atFactory} comes with Production (M10) and {@code owed} with factory payments – null until then. */
    public record FactoryResponse(
            long id,
            String code,
            String name,
            @Schema(nullable = true) String address,
            @Schema(nullable = true) String contact,
            @Schema(nullable = true) String phone,
            @Schema(nullable = true) String email,
            @Schema(nullable = true) String licence,
            int termsDays,
            String termsLabel,
            List<FactoryChargeView> charges,
            @Schema(nullable = true) Integer atFactory,
            @Schema(nullable = true) BigDecimal owed,
            boolean active,
            long version) {
    }

    /**
     * New or changed factory. {@code charges} = charge per bottle for every active bottle type, e.g. {"B20": 60, "B10": 35}.
     * A changed charge is saved from today; batches already sent keep their charge.
     */
    public record FactoryRequest(
            @NotBlank(message = "Enter the factory name.") @Size(max = 100, message = "Name: at most 100 characters.") String name,
            @Size(max = 200, message = "Address: at most 200 characters.") String address,
            @Size(max = 100, message = "Contact: at most 100 characters.") String contact,
            @Size(max = 20, message = "Phone: at most 20 characters.") String phone,
            @Size(max = 100, message = "Email: at most 100 characters.") String email,
            @Size(max = 100, message = "Licence: at most 100 characters.") String licence,
            Integer termsDays,
            Map<String, BigDecimal> charges,
            Boolean active,
            Long version) {
    }

    /** A row of a factory's charge history; {@code current} = the charge in force today for that bottle type. */
    public record FactoryChargeHistoryRow(long id, String bottleTypeCode, BigDecimal charge, LocalDate effectiveFrom,
                                          String createdBy, Instant createdAt, boolean current) {
    }
}
