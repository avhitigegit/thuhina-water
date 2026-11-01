package lk.thuhina.water.sales.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.sales.dto.SalesDtos.BillRequest;
import lk.thuhina.water.sales.dto.SalesDtos.CollectEmptiesRequest;
import lk.thuhina.water.sales.dto.SalesDtos.CustomerDamageRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ExtraOrderRequest;
import lk.thuhina.water.sales.dto.SalesDtos.FirstPurchaseRequest;
import lk.thuhina.water.sales.dto.SalesDtos.NextPaperBill;
import lk.thuhina.water.sales.dto.SalesDtos.PaperBillCheck;
import lk.thuhina.water.sales.dto.SalesDtos.ProductSaleRequest;
import lk.thuhina.water.sales.dto.SalesDtos.ReverseRequest;
import lk.thuhina.water.sales.dto.SalesDtos.SalePreview;
import lk.thuhina.water.sales.dto.SalesDtos.SaleResult;
import lk.thuhina.water.sales.dto.SalesDtos.SettleOwedRequest;
import lk.thuhina.water.sales.dto.SalesDtos.TxnDetail;
import lk.thuhina.water.sales.dto.SalesDtos.TxnListResponse;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.service.SalesQueryService;
import lk.thuhina.water.sales.service.SalesService;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales &amp; Bills (design 7.2 Sales). Admin only (Accountant and Delivery Staff have no access). Each sale has a
 * {@code /preview} with the same body that saves nothing. Business errors: CREDIT_LIMIT (Admin may resend with
 * approveOverLimit = true), DUPLICATE_PAPER_BILL, CUSTOMER_INACTIVE, NOTE_REQUIRED, STOCK_NEGATIVE, SALE_RULE.
 */
@Tag(name = "Sales")
@RestController
@RequestMapping("/sales")
public class SalesController {

    private static final String VIEW = "hasAuthority('" + Permissions.SALES_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.SALES_EDIT + "')";

    private final SalesService service;
    private final SalesQueryService queries;

    public SalesController(SalesService service, SalesQueryService queries) {
        this.service = service;
        this.queries = queries;
    }

    @Operation(summary = "Exchange or Left at door from a paper bill (paper bill number required); moves the next delivery on")
    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult bill(@Valid @RequestBody BillRequest request) {
        return service.postBill(request);
    }

    @Operation(summary = "Live calculation of a delivery bill – nothing is saved")
    @PostMapping("/bills/preview")
    @PreAuthorize(EDIT)
    public SalePreview billPreview(@RequestBody BillRequest request) {
        return service.previewBill(request);
    }

    @Operation(summary = "First purchase: water + deposit per bottle; accepted old bottles waive the deposit")
    @PostMapping("/first-purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult firstPurchase(@Valid @RequestBody FirstPurchaseRequest request) {
        return service.firstPurchase(request);
    }

    @Operation(summary = "Live calculation of a first purchase")
    @PostMapping("/first-purchase/preview")
    @PreAuthorize(EDIT)
    public SalePreview firstPurchasePreview(@RequestBody FirstPurchaseRequest request) {
        return service.previewFirstPurchase(request);
    }

    @Operation(summary = "Extra order: deposits only for bottles beyond the empties returned")
    @PostMapping("/extra-order")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult extraOrder(@Valid @RequestBody ExtraOrderRequest request) {
        return service.extraOrder(request);
    }

    @Operation(summary = "Live calculation of an extra order")
    @PostMapping("/extra-order/preview")
    @PreAuthorize(EDIT)
    public SalePreview extraOrderPreview(@RequestBody ExtraOrderRequest request) {
        return service.previewExtraOrder(request);
    }

    @Operation(summary = "Collect empties left at the door earlier (no charge)")
    @PostMapping("/collect-empties")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult collectEmpties(@Valid @RequestBody CollectEmptiesRequest request) {
        return service.collectEmpties(request);
    }

    @Operation(summary = "Live calculation of collecting empties")
    @PostMapping("/collect-empties/preview")
    @PreAuthorize(EDIT)
    public SalePreview collectEmptiesPreview(@RequestBody CollectEmptiesRequest request) {
        return service.previewCollectEmpties(request);
    }

    @Operation(summary = "Customer-damaged bottle: REPLACE (charge deposit, + water when given filled) or REDUCE the holding")
    @PostMapping("/customer-damage")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult customerDamage(@Valid @RequestBody CustomerDamageRequest request) {
        return service.customerDamage(request);
    }

    @Operation(summary = "Live calculation of a customer-damaged bottle")
    @PostMapping("/customer-damage/preview")
    @PreAuthorize(EDIT)
    public SalePreview customerDamagePreview(@RequestBody CustomerDamageRequest request) {
        return service.previewCustomerDamage(request);
    }

    @Operation(summary = "Settle bottles owed: RETURN the bottles or pay the DEPOSIT")
    @PostMapping("/settle-owed")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult settleOwed(@Valid @RequestBody SettleOwedRequest request) {
        return service.settleOwed(request);
    }

    @Operation(summary = "Live calculation of settling bottles owed")
    @PostMapping("/settle-owed/preview")
    @PreAuthorize(EDIT)
    public SalePreview settleOwedPreview(@RequestBody SettleOwedRequest request) {
        return service.previewSettleOwed(request);
    }

    @Operation(summary = "Product sale to a customer or a walk-in (walk-in pays in full)")
    @PostMapping("/product-sale")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult productSale(@Valid @RequestBody ProductSaleRequest request) {
        return service.productSale(request);
    }

    @Operation(summary = "Live calculation of a product sale (with discount and stock warnings)")
    @PostMapping("/product-sale/preview")
    @PreAuthorize(EDIT)
    public SalePreview productSalePreview(@RequestBody ProductSaleRequest request) {
        return service.previewProductSale(request);
    }

    @Operation(summary = "Reverse a bill with a reason – a REVERSAL bill cancels its stock, bottles and money")
    @PostMapping("/txns/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public SaleResult reverse(@PathVariable long id, @Valid @RequestBody ReverseRequest request) {
        return service.reverse(id, request);
    }

    @Operation(summary = "Bills, newest first. q = part of the system or paper bill number; kind; customerId; from / to dates")
    @GetMapping("/txns")
    @PreAuthorize(VIEW)
    public TxnListResponse list(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Long customerId,
                                @RequestParam(required = false) SalesKind kind,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) Boolean onPhone,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        return queries.list(new SalesQueryService.Filter(q, customerId, kind, from, to, onPhone), page, size);
    }

    @Operation(summary = "Bill details: lines, amounts, ledger effects, user, links to its reversal")
    @GetMapping("/txns/{id}")
    @PreAuthorize(VIEW)
    public TxnDetail detail(@PathVariable long id) {
        return queries.detail(id);
    }

    @Operation(summary = "Suggested next paper bill number for Enter Bills (highest numeric paper bill number + 1)")
    @GetMapping("/paper-bill/next")
    @PreAuthorize(EDIT)
    public NextPaperBill nextPaperBill() {
        return queries.nextPaperBill();
    }

    @Operation(summary = "Is this paper bill number already entered? (\"Bill 41275 has not been entered yet.\")")
    @GetMapping("/paper-bill/{no}")
    @PreAuthorize(VIEW)
    public PaperBillCheck paperBill(@PathVariable String no) {
        return queries.paperBill(no);
    }
}
