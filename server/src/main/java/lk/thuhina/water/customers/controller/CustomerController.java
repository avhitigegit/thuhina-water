package lk.thuhina.water.customers.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerDetail;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerListResponse;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.CustomerSaved;
import lk.thuhina.water.customers.dto.CustomerDtos.DeactivateRequest;
import lk.thuhina.water.customers.dto.CustomerDtos.HistoryRow;
import lk.thuhina.water.customers.dto.CustomerDtos.LookupRow;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.customers.service.CustomerQueryService;
import lk.thuhina.water.customers.service.CustomerService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Customers (design 7.2 Customers). Admin full; Accountant view. */
@Tag(name = "Customers")
@RestController
@RequestMapping("/customers")
public class CustomerController {

    private static final String VIEW = "hasAuthority('" + Permissions.CUSTOMERS_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.CUSTOMERS_EDIT + "')";

    private final CustomerService service;
    private final CustomerQueryService queries;

    public CustomerController(CustomerService service, CustomerQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @Operation(summary = "Customers with search (name, code, address; phone digits from 3), filters and paging. "
            + "status ACTIVE (default) / INACTIVE / ALL; price STANDARD / AGREED; day 1 = Monday … 7 = Sunday")
    @GetMapping
    @PreAuthorize(VIEW)
    public CustomerListResponse list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Long area,
                                     @RequestParam(required = false) Integer day,
                                     @RequestParam(required = false) Long type,
                                     @RequestParam(required = false) PayType pay,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String price,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String sort) {
        return queries.list(new CustomerQueryService.Filter(q, area, day, type, pay, status, price), page, size, sort);
    }

    @Operation(summary = "Picker lookup: code (C0017), number (17), phone digits (9+) or part of the name; at most 10")
    @GetMapping("/lookup")
    @PreAuthorize(VIEW)
    public List<LookupRow> lookup(@RequestParam String q) {
        return queries.lookup(q);
    }

    @Operation(summary = "Everything for the side panel: details, balances, outstanding, delivery, water prices")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public CustomerDetail detail(@PathVariable long id) {
        return queries.detail(id);
    }

    @Operation(summary = "Customer history – registration, changes, status and prices (sales and payments come later)")
    @GetMapping("/{id}/history")
    @PreAuthorize(VIEW)
    public List<HistoryRow> history(@PathVariable long id) {
        return queries.history(id);
    }

    @Operation(summary = "Register a customer (agreed prices optional, from the registration date). "
            + "A phone already in use gives DUP_PHONE unless allowDuplicatePhone = true")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public CustomerSaved register(@Valid @RequestBody CustomerRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Edit a customer")
    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public CustomerSaved update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Deactivate from a date with a reason – history is kept")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize(EDIT)
    public ResponseEntity<Void> deactivate(@PathVariable long id, @Valid @RequestBody DeactivateRequest request) {
        service.deactivate(id, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reactivate – a past next delivery moves to the next delivery day from today")
    @PostMapping("/{id}/reactivate")
    @PreAuthorize(EDIT)
    public ResponseEntity<Void> reactivate(@PathVariable long id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }
}
