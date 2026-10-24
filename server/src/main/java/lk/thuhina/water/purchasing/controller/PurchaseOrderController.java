package lk.thuhina.water.purchasing.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoUpdateRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReasonRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptResult;
import lk.thuhina.water.purchasing.service.PurchaseOrderService;
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

/** Purchase orders and goods receipts (design 7.2 Purchasing). Each step is its own action. Admin full, Accountant view. */
@Tag(name = "Purchasing – purchase orders")
@RestController
@RequestMapping("/purchase-orders")
public class PurchaseOrderController {

    private static final String VIEW = "hasAuthority('" + Permissions.PURCHASING_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.PURCHASING_EDIT + "')";

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @Operation(summary = "Purchase orders, newest first, with the next step (status optional)")
    @GetMapping
    @PreAuthorize(VIEW)
    public List<PoRow> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @Operation(summary = "A purchase order with lines, history and receipts")
    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public PoDetail detail(@PathVariable long id) {
        return service.detail(id);
    }

    @Operation(summary = "Change a Draft: quantities and prices (qty 0 removes the line), expected date, notes")
    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public PoDetail update(@PathVariable long id, @Valid @RequestBody PoUpdateRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Draft → Approved")
    @PostMapping("/{id}/approve")
    @PreAuthorize(EDIT)
    public PoDetail approve(@PathVariable long id) {
        return service.approve(id);
    }

    @Operation(summary = "Approved → Sent (marked as sent to the supplier)")
    @PostMapping("/{id}/send")
    @PreAuthorize(EDIT)
    public PoDetail send(@PathVariable long id) {
        return service.send(id);
    }

    @Operation(summary = "Cancel a Draft or Approved PO (reason required)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(EDIT)
    public PoDetail cancel(@PathVariable long id, @Valid @RequestBody ReasonRequest request) {
        return service.cancel(id, request);
    }

    @Operation(summary = "Receive goods (GRN-0001) against a Sent / Partly received PO – good items go into stock")
    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public ReceiptResult receive(@PathVariable long id, @Valid @RequestBody ReceiptRequest request) {
        return service.receive(id, request);
    }
}
