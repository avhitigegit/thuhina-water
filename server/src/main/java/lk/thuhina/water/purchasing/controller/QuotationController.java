package lk.thuhina.water.purchasing.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.AttachmentResponse;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QuotationRequestBody;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReasonRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierLink;
import lk.thuhina.water.purchasing.service.PurchaseOrderService;
import lk.thuhina.water.purchasing.service.QuotationService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Quotation requests and supplier quotations (design 7.2 Purchasing). Admin full, Accountant view. */
@Tag(name = "Purchasing – quotations")
@RestController
public class QuotationController {

    private static final String VIEW = "hasAuthority('" + Permissions.PURCHASING_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.PURCHASING_EDIT + "')";

    private final QuotationService service;
    private final PurchaseOrderService orders;

    public QuotationController(QuotationService service, PurchaseOrderService orders) {
        this.service = service;
        this.orders = orders;
    }

    @Operation(summary = "Quotation requests, newest first (status OPEN / QUOTES_RECEIVED / SELECTED / CLOSED optional)")
    @GetMapping("/quotation-requests")
    @PreAuthorize(VIEW)
    public List<QrRow> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @Operation(summary = "Active suppliers of any of the items – ticked for the user on a new request")
    @GetMapping("/quotation-requests/suggested-suppliers")
    @PreAuthorize(VIEW)
    public List<SupplierLink> suggestedSuppliers(@RequestParam(required = false) List<String> items) {
        return service.suggestedSuppliers(items);
    }

    @Operation(summary = "A request with the side-by-side comparison of its quotations")
    @GetMapping("/quotation-requests/{id}")
    @PreAuthorize(VIEW)
    public QrDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(summary = "New quotation request – number QR-2026-001 on save; items with qty > 0; suppliers asked")
    @PostMapping("/quotation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public QrDetail create(@Valid @RequestBody QrRequest request) {
        return service.create(request);
    }

    @Operation(summary = "Record a supplier quotation (SQ-0001) – unit price per item, blank = not quoted")
    @PostMapping("/quotation-requests/{id}/quotations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public QrDetail addQuotation(@PathVariable long id, @Valid @RequestBody QuotationRequestBody request) {
        return service.addQuotation(id, request);
    }

    @Operation(summary = "Choose a quotation (reason required; not when expired; one per request)")
    @PostMapping("/quotations/{id}/choose")
    @PreAuthorize(EDIT)
    public QrDetail choose(@PathVariable long id, @Valid @RequestBody ReasonRequest request) {
        return service.choose(id, request);
    }

    @Operation(summary = "Create the purchase order (Draft) from the chosen quotation")
    @PostMapping("/quotations/{id}/create-po")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public PoDetail createPo(@PathVariable long id) {
        return orders.createFromQuotation(id);
    }

    @Operation(summary = "Attach the scanned quotation (PDF, JPG or PNG, at most 5 MB); replaces an earlier file")
    @PostMapping(value = "/quotations/{id}/attachment", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(EDIT)
    public AttachmentResponse attach(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        return service.attach(id, file);
    }

    @Operation(summary = "The scanned quotation – opens in the browser")
    @GetMapping("/quotations/{id}/attachment")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> attachment(@PathVariable long id) {
        QuotationService.Attachment a = service.attachment(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(a.name()).build().toString())
                .contentType(a.mediaType())
                .body(a.content());
    }
}
