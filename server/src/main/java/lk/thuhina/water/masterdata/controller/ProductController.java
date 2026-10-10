package lk.thuhina.water.masterdata.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.masterdata.dto.ProductDtos.CreateProductRequest;
import lk.thuhina.water.masterdata.dto.ProductDtos.ProductResponse;
import lk.thuhina.water.masterdata.dto.ProductDtos.UpdateProductRequest;
import lk.thuhina.water.masterdata.service.ProductService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Other products (FR-02). */
@Tag(name = "Products")
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @Operation(summary = "All products by code")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_VIEW + "')")
    public List<ProductResponse> list() {
        return service.list();
    }

    @Operation(summary = "New product – code P01… on save; opening stock only here")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change name, prices or active – not the stock")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        return service.update(id, request);
    }
}
