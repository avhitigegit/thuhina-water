package lk.thuhina.water.masterdata.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.AreaRequest;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.AreaResponse;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CreateCustomerTypeRequest;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.CustomerTypeResponse;
import lk.thuhina.water.masterdata.dto.CustomerTypeAreaDtos.UpdateCustomerTypeRequest;
import lk.thuhina.water.masterdata.service.AreaService;
import lk.thuhina.water.masterdata.service.CustomerTypeService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer types (FR-03) and areas. Lists are reference data for every screen; changes need Bottles &amp; Products or
 * Customers edit rights (the pop-up for them is on the Customers page, M05).
 */
@Tag(name = "Customer types and areas")
@RestController
public class CustomerTypeAreaController {

    private static final String EDIT = "hasAnyAuthority('" + Permissions.MASTERDATA_EDIT + "', '" + Permissions.CUSTOMERS_EDIT + "')";

    private final CustomerTypeService types;
    private final AreaService areas;

    public CustomerTypeAreaController(CustomerTypeService types, AreaService areas) {
        this.types = types;
        this.areas = areas;
    }

    @Operation(summary = "Customer types in the order they were added")
    @GetMapping("/customer-types")
    @PreAuthorize("isAuthenticated()")
    public List<CustomerTypeResponse> customerTypes() {
        return types.list();
    }

    @Operation(summary = "New customer type (unique name)")
    @PostMapping("/customer-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public CustomerTypeResponse createCustomerType(@Valid @RequestBody CreateCustomerTypeRequest request) {
        return types.create(request);
    }

    @Operation(summary = "Change the description or active – the name cannot change")
    @PutMapping("/customer-types/{id}")
    @PreAuthorize(EDIT)
    public CustomerTypeResponse updateCustomerType(@PathVariable long id, @Valid @RequestBody UpdateCustomerTypeRequest request) {
        return types.update(id, request);
    }

    @Operation(summary = "Areas (delivery routes) by name")
    @GetMapping("/areas")
    @PreAuthorize("isAuthenticated()")
    public List<AreaResponse> areas() {
        return areas.list();
    }

    @Operation(summary = "New area (unique name)")
    @PostMapping("/areas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public AreaResponse createArea(@Valid @RequestBody AreaRequest request) {
        return areas.create(request);
    }

    @Operation(summary = "Rename an area or change active")
    @PutMapping("/areas/{id}")
    @PreAuthorize(EDIT)
    public AreaResponse updateArea(@PathVariable long id, @Valid @RequestBody AreaRequest request) {
        return areas.update(id, request);
    }
}
