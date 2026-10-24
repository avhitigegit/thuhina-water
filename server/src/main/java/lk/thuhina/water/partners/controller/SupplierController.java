package lk.thuhina.water.partners.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.partners.dto.SupplierDtos.SupplierRequest;
import lk.thuhina.water.partners.dto.SupplierDtos.SupplierResponse;
import lk.thuhina.water.partners.service.SupplierService;
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

/** Suppliers (design 7.2 Partners). Admin full, Accountant view. */
@Tag(name = "Suppliers")
@RestController
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @Operation(summary = "All suppliers by code; with item=B20 (or a product code) only the active suppliers of that item")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_VIEW + "')")
    public List<SupplierResponse> list(@RequestParam(required = false) String item) {
        return service.list(item);
    }

    @Operation(summary = "New supplier – code S01… on save; at least one supplied item; terms 0/7/14/30/45/60 days")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_EDIT + "')")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change a supplier")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_EDIT + "')")
    public SupplierResponse update(@PathVariable long id, @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request);
    }
}
