package lk.thuhina.water.customers.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerPriceRow;
import lk.thuhina.water.customers.dto.CustomerDtos.EndCustomerPriceRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.SetCustomerPriceRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.WaterPriceResponse;
import lk.thuhina.water.customers.service.CustomerPriceService;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Agreed customer prices (FR-05, BR-08): Admin sets / changes, Accountant views. */
@Tag(name = "Customer prices")
@RestController
public class CustomerPriceController {

    private static final String VIEW = "hasAuthority('" + Permissions.CUSTOMER_PRICES_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.CUSTOMER_PRICES_EDIT + "')";

    private final CustomerPriceService prices;
    private final PricingService pricing;
    private final Clock clock;

    public CustomerPriceController(CustomerPriceService prices, PricingService pricing, Clock clock) {
        this.prices = prices;
        this.pricing = pricing;
        this.clock = clock;
    }

    @Operation(summary = "A customer's agreed prices, newest first, with In force / Scheduled / Ended / Replaced / Cancelled")
    @GetMapping("/customers/{id}/prices")
    @PreAuthorize(VIEW)
    public List<CustomerPriceRow> history(@PathVariable long id) {
        return prices.history(id);
    }

    @Operation(summary = "Set an agreed price from a date (optionally until a date); closes or cancels overlapping ones")
    @PostMapping("/customers/{id}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public CustomerPriceRow set(@PathVariable long id, @Valid @RequestBody SetCustomerPriceRequest request) {
        return prices.set(id, request);
    }

    @Operation(summary = "Back to the standard price from a date")
    @PostMapping("/customers/{id}/prices/end")
    @PreAuthorize(EDIT)
    public ResponseEntity<Void> end(@PathVariable long id, @Valid @RequestBody EndCustomerPriceRequest request) {
        prices.end(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "All customers' agreed prices: status=current (in force and scheduled, default) or all")
    @GetMapping("/customer-prices")
    @PreAuthorize(VIEW)
    public List<CustomerPriceRow> list(@RequestParam(defaultValue = "current") String status) {
        return prices.list("all".equalsIgnoreCase(status));
    }

    @Operation(summary = "Water price for a customer, bottle type and date (default today): agreed price first, else standard")
    @GetMapping("/pricing/water")
    @PreAuthorize("hasAnyAuthority('" + Permissions.CUSTOMERS_VIEW + "', '" + Permissions.SALES_VIEW + "', '"
            + Permissions.QUOTATIONS_VIEW + "')")
    public WaterPriceResponse waterPrice(@RequestParam long customerId, @RequestParam String bottleType,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date == null ? BusinessDates.today(clock) : date;
        return pricing.waterPrice(customerId, bottleType, day)
                .map(w -> new WaterPriceResponse(w.price(), w.agreed(), w.standard()))
                .orElseThrow(() -> new NotFoundException("Water price", bottleType));
    }
}
