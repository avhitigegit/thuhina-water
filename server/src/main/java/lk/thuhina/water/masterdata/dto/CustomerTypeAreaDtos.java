package lk.thuhina.water.masterdata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Customer types (FR-03) and areas (delivery routes). */
public final class CustomerTypeAreaDtos {

    private CustomerTypeAreaDtos() {
    }

    public record CustomerTypeResponse(long id, String name, @Schema(nullable = true) String description, boolean active,
                                       long version) {
    }

    public record CreateCustomerTypeRequest(
            @NotBlank(message = "Enter the customer type name.") @Size(max = 50, message = "Name: at most 50 characters.") String name,
            @Size(max = 200, message = "Description: at most 200 characters.") String description) {
    }

    /** The name cannot be changed (price lists use it). */
    public record UpdateCustomerTypeRequest(
            @Size(max = 200, message = "Description: at most 200 characters.") String description,
            boolean active,
            Long version) {
    }

    public record AreaResponse(long id, String name, boolean active, long version) {
    }

    public record AreaRequest(
            @NotBlank(message = "Enter the area name.") @Size(max = 60, message = "Name: at most 60 characters.") String name,
            Boolean active,
            Long version) {
    }
}
