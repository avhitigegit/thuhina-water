package lk.thuhina.water.quotations.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqAcceptRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqApplyRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqItemOption;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqListResponse;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqRejectRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqSaved;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqSendRequest;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqView;
import lk.thuhina.water.quotations.service.CustomerQuotationQueryService;
import lk.thuhina.water.quotations.service.CustomerQuotationService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Customer quotations (design 7.2 Customer quotations). Admin full; Accountant view and print; Delivery Staff none. */
@Tag(name = "Customer quotations")
@RestController
@RequestMapping("/customer-quotations")
public class CustomerQuotationController {

    private static final String VIEW = "hasAuthority('" + Permissions.QUOTATIONS_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.QUOTATIONS_EDIT + "')";

    private final CustomerQuotationService service;
    private final CustomerQuotationQueryService query;

    public CustomerQuotationController(CustomerQuotationService service, CustomerQuotationQueryService query) {
        this.service = service;
        this.query = query;
    }

    @Operation(summary = "Quotations newest first; status DRAFT / SENT / EXPIRED / ACCEPTED / REJECTED and search optional; with the KPIs")
    @GetMapping
    @PreAuthorize(VIEW)
    public CqListResponse list(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return query.list(status, q);
    }

    @Operation(summary = "What can be quoted with the price on a date: the customer's price now, or the standard price of the customer type")
    @GetMapping("/items")
    @PreAuthorize(VIEW)
    public List<CqItemOption> items(@RequestParam(required = false) Long customerTypeId, @RequestParam(required = false) Long customerId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return query.items(customerTypeId, customerId, date);
    }

    @Operation(summary = "One quotation with lines, history, the next steps, the email text and the register pre-fill")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public CqView view(@PathVariable long id) {
        return query.view(id);
    }

    @Operation(summary = "New draft (QT-2026-001); copyOf for Make a copy")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public CqSaved create(@Valid @RequestBody CqRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Change a draft (only a draft can change)")
    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public CqSaved update(@PathVariable long id, @Valid @RequestBody CqRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Mark as sent (EMAIL / BY_HAND / POST / WHATSAPP, date default today) – drafts only")
    @PostMapping("/{id}/send")
    @PreAuthorize(EDIT)
    public CqSaved send(@PathVariable long id, @RequestBody CqSendRequest request) {
        return service.send(id, request.via(), request.date());
    }

    @Operation(summary = "Accepted (date default today) – sent quotations only, also when expired")
    @PostMapping("/{id}/accept")
    @PreAuthorize(EDIT)
    public CqSaved accept(@PathVariable long id, @RequestBody(required = false) CqAcceptRequest request) {
        return service.accept(id, request == null ? null : request.date());
    }

    @Operation(summary = "Not accepted, with the reason – sent quotations only")
    @PostMapping("/{id}/reject")
    @PreAuthorize(EDIT)
    public CqSaved reject(@PathVariable long id, @Valid @RequestBody CqRejectRequest request) {
        return service.reject(id, request.reason());
    }

    @Operation(summary = "Apply the quoted water prices as the customer's agreed prices from a date (default today)")
    @PostMapping("/{id}/apply-prices")
    @PreAuthorize(EDIT + " and hasAuthority('" + Permissions.CUSTOMER_PRICES_EDIT + "')")
    public CqSaved applyPrices(@PathVariable long id, @RequestBody(required = false) CqApplyRequest request) {
        return service.applyPrices(id, request == null ? null : request.from());
    }
}
