package lk.thuhina.water.partners.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Suppliers tab (FR-14). */
public final class SupplierDtos {

    public static final String MSG_REQUIRED = "Supplier name and phone are required.";

    private SupplierDtos() {
    }

    /** An item the supplier supplies: {@code type} BOTTLE or PRODUCT, {@code name} as shown ("Empty 20L Bottle"). */
    public record SuppliedItem(String code, String type, String name) {
    }

    /** {@code owed} comes with Purchasing (M09) – null until then. */
    public record SupplierResponse(
            long id,
            String code,
            String name,
            @Schema(nullable = true) String contact,
            String phone,
            @Schema(nullable = true) String email,
            @Schema(nullable = true) String address,
            int termsDays,
            String termsLabel,
            List<SuppliedItem> items,
            @Schema(nullable = true) BigDecimal owed,
            boolean active,
            long version) {
    }

    /**
     * New or changed supplier. {@code items} = bottle type codes (B20) and product codes (P04);
     * {@code termsDays} from the fixed list 0, 7, 14, 30, 45, 60.
     */
    public record SupplierRequest(
            @NotBlank(message = MSG_REQUIRED) @Size(max = 100, message = "Name: at most 100 characters.") String name,
            @Size(max = 100, message = "Contact: at most 100 characters.") String contact,
            @NotBlank(message = MSG_REQUIRED) @Size(max = 20, message = "Phone: at most 20 characters.") String phone,
            @Size(max = 100, message = "Email: at most 100 characters.") String email,
            @Size(max = 200, message = "Address: at most 200 characters.") String address,
            Integer termsDays,
            List<String> items,
            Boolean active,
            Long version) {
    }
}
