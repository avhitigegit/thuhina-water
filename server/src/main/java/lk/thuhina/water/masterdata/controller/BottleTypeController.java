package lk.thuhina.water.masterdata.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.BottleTypeResponse;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.CreateBottleTypeRequest;
import lk.thuhina.water.masterdata.dto.BottleTypeDtos.UpdateBottleTypeRequest;
import lk.thuhina.water.masterdata.service.BottleTypeService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bottle types (design 7.2 Master data). The list is reference data for every screen; changes are Admin only. */
@Tag(name = "Bottle types")
@RestController
@RequestMapping("/bottle-types")
public class BottleTypeController {

    private final BottleTypeService service;

    public BottleTypeController(BottleTypeService service) {
        this.service = service;
    }

    @Operation(summary = "Bottle types, largest first; activeOnly=true for the pick lists of sales and purchasing")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BottleTypeResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @Operation(summary = "New bottle type – the code is made from the size (19 → B19)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public BottleTypeResponse create(@Valid @RequestBody CreateBottleTypeRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change the name or active – the size cannot change")
    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public BottleTypeResponse update(@PathVariable String code, @Valid @RequestBody UpdateBottleTypeRequest request) {
        return service.update(code, request);
    }
}
