package lk.thuhina.water.partners.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryChargeHistoryRow;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryRequest;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryResponse;
import lk.thuhina.water.partners.service.FactoryService;
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

/** Filling factories (design 7.2 Partners). Admin full, Accountant view. */
@Tag(name = "Filling factories")
@RestController
@RequestMapping("/factories")
public class FactoryController {

    private final FactoryService service;

    public FactoryController(FactoryService service) {
        this.service = service;
    }

    @Operation(summary = "All factories by code, with today's charge per active bottle type")
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_VIEW + "')")
    public List<FactoryResponse> list() {
        return service.list();
    }

    @Operation(summary = "A factory's charge history, newest first")
    @GetMapping("/{id}/charges")
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_VIEW + "')")
    public List<FactoryChargeHistoryRow> charges(@PathVariable long id) {
        return service.chargeHistory(id);
    }

    @Operation(summary = "New factory – code F01… on save; a charge > 0 for every active bottle type")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_EDIT + "')")
    public FactoryResponse create(@Valid @RequestBody FactoryRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change a factory; a changed charge is saved from today and applies to batches sent after saving")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PARTNERS_EDIT + "')")
    public FactoryResponse update(@PathVariable long id, @Valid @RequestBody FactoryRequest request) {
        return service.update(id, request);
    }
}
