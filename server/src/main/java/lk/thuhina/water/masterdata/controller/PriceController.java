package lk.thuhina.water.masterdata.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceHistoryRow;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceMatrixResponse;
import lk.thuhina.water.masterdata.dto.PriceDtos.SetPriceRequest;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Standard water prices and deposits (FR-04, FR-07, BR-08). */
@Tag(name = "Prices")
@RestController
@RequestMapping("/prices")
public class PriceController {

    private final PricingService pricing;

    public PriceController(PricingService pricing) {
        this.pricing = pricing;
    }

    @Operation(summary = "Water price grid and deposits on a date (default today), with the next scheduled change")
    @GetMapping("/current")
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_VIEW + "')")
    public PriceMatrixResponse current(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return pricing.currentMatrix(date);
    }

    @Operation(summary = "Every price entry, newest first, marked Current / Scheduled / Old")
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_VIEW + "')")
    public List<PriceHistoryRow> history() {
        return pricing.history();
    }

    @Operation(summary = "Change a price from a date (today or later) with a reason – a new entry; history is kept")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.MASTERDATA_EDIT + "')")
    public PriceHistoryRow setPrice(@Valid @RequestBody SetPriceRequest request) {
        return pricing.setPrice(request);
    }
}
