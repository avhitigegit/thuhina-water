package lk.thuhina.water.purchasing.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PaymentRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PaymentRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierBalance;
import lk.thuhina.water.purchasing.service.SupplierPaymentService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Amounts owed to suppliers and payments (design 7.2 Purchasing). Admin full, Accountant view. */
@Tag(name = "Purchasing – supplier payments")
@RestController
public class SupplierPaymentController {

    private final SupplierPaymentService service;

    public SupplierPaymentController(SupplierPaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Owed per supplier = goods received − paid, with the oldest unpaid receipt")
    @GetMapping("/supplier-balances")
    @PreAuthorize("hasAuthority('" + Permissions.PURCHASING_VIEW + "')")
    public List<SupplierBalance> balances() {
        return service.balances();
    }

    @Operation(summary = "Payments made, newest first (supplier optional)")
    @GetMapping("/supplier-payments")
    @PreAuthorize("hasAuthority('" + Permissions.PURCHASING_VIEW + "')")
    public List<PaymentRow> list(@RequestParam(required = false) Long supplier) {
        return service.list(supplier);
    }

    @Operation(summary = "Record a payment (SP-0001) – reference required unless cash")
    @PostMapping("/supplier-payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + Permissions.PURCHASING_EDIT + "')")
    public PaymentRow create(@Valid @RequestBody PaymentRequest request) {
        return service.create(request);
    }
}
