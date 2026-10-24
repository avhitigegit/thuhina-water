package lk.thuhina.water.masterdata.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.CreateOldBottleBrandRequest;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.OldBottleBrandResponse;
import lk.thuhina.water.masterdata.dto.OldBottleBrandDtos.UpdateOldBottleBrandRequest;
import lk.thuhina.water.masterdata.service.OldBottleBrandService;
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

/** Accepted old-bottle brands (FR-06, BR-02). */
@Tag(name = "Old bottle brands")
@RestController
@RequestMapping("/old-bottle-brands")
public class OldBottleBrandController {

    private final OldBottleBrandService service;

    public OldBottleBrandController(OldBottleBrandService service) {
        this.service = service;
    }

    @Operation(summary = "Accepted brands, active first")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_VIEW + "')")
    public List<OldBottleBrandResponse> list() {
        return service.list();
    }

    @Operation(summary = "Add an accepted brand for a bottle type")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public OldBottleBrandResponse create(@Valid @RequestBody CreateOldBottleBrandRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Stop accepting / accept again, or change the note or bottle type")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public OldBottleBrandResponse update(@PathVariable long id, @Valid @RequestBody UpdateOldBottleBrandRequest request) {
        return service.update(id, request);
    }
}
