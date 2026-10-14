package lk.thuhina.water.printing;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Printed documents as PDF (design 7.2 Print). More documents are added by later modules. */
@Tag(name = "Print")
@RestController
@RequestMapping("/print")
public class PrintController {

    private final BillPrintService bills;
    private final DeliveryPrintService delivery;
    private final PurchaseOrderPrintService purchaseOrders;
    private final DispatchNotePrintService dispatchNotes;
    private final BillingPrintService billing;

    public PrintController(BillPrintService bills, DeliveryPrintService delivery, PurchaseOrderPrintService purchaseOrders,
                           DispatchNotePrintService dispatchNotes, BillingPrintService billing) {
        this.bills = bills;
        this.delivery = delivery;
        this.purchaseOrders = purchaseOrders;
        this.dispatchNotes = dispatchNotes;
        this.billing = billing;
    }

    @Operation(summary = "Bill PDF (A5) – opens in the browser to print")
    @GetMapping(value = "/bill/{txnId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.SALES_VIEW + "')")
    public ResponseEntity<byte[]> bill(@PathVariable long txnId) {
        return pdf(bills.bill(txnId), "bill-" + bills.billNo(txnId) + ".pdf");
    }

    @Operation(summary = "Delivery sheet PDF (A4 landscape) – the Daily Delivery List of a date (default today) by area")
    @GetMapping(value = "/delivery-sheet", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyAuthority('" + Permissions.SALES_EDIT + "', '" + Permissions.DELIVERY_PLANNING_VIEW + "', '"
            + Permissions.DELIVERY_LIST_VIEW + "')")
    public ResponseEntity<byte[]> deliverySheet(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                @RequestParam(required = false) Long area) {
        byte[] pdf = delivery.deliverySheet(date, area);
        return pdf(pdf, "delivery-sheet-" + (date == null ? "today" : date) + ".pdf");
    }

    @Operation(summary = "Load sheet PDF (A4 landscape) – route totals and the customers due on a date (default tomorrow)")
    @GetMapping(value = "/load-sheet", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.DELIVERY_PLANNING_VIEW + "')")
    public ResponseEntity<byte[]> loadSheet(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                            @RequestParam(required = false) Long area) {
        byte[] pdf = delivery.loadSheet(date, area);
        return pdf(pdf, "load-sheet-" + (date == null ? "tomorrow" : date) + ".pdf");
    }

    @Operation(summary = "Purchase order PDF (A4) – DRAFT / CANCELLED watermark while not valid for supply")
    @GetMapping(value = "/purchase-order/{id}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.PURCHASING_VIEW + "')")
    public ResponseEntity<byte[]> purchaseOrder(@PathVariable long id) {
        return pdf(purchaseOrders.purchaseOrder(id), purchaseOrders.poNo(id) + ".pdf");
    }

    @Operation(summary = "Dispatch note PDF (A4) – every bottle type sent to the factory in one dispatch")
    @GetMapping(value = "/dispatch-note/{dispatchNo}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTION_VIEW + "')")
    public ResponseEntity<byte[]> dispatchNote(@PathVariable String dispatchNo) {
        return pdf(dispatchNotes.dispatchNote(dispatchNo), dispatchNo + ".pdf");
    }

    @Operation(summary = "Payment receipt PDF (A5) – amount, how paid, balance before and now")
    @GetMapping(value = "/receipt/{paymentId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.BILLING_VIEW + "')")
    public ResponseEntity<byte[]> receipt(@PathVariable long paymentId) {
        return pdf(billing.receipt(paymentId), billing.receiptNo(paymentId) + ".pdf");
    }

    @Operation(summary = "Monthly invoice PDF (A4) – a line per delivery, previous balance, payments, this month, total due")
    @GetMapping(value = "/invoice/{id}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.BILLING_VIEW + "')")
    public ResponseEntity<byte[]> invoice(@PathVariable long id) {
        return pdf(billing.invoice(id), billing.invoiceNo(id) + ".pdf");
    }

    @Operation(summary = "Statement of account PDF (A4) – from default the start of last month, to default today")
    @GetMapping(value = "/statement/{customerId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.BILLING_VIEW + "')")
    public ResponseEntity<byte[]> statement(@PathVariable long customerId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return pdf(billing.statement(customerId, from, to), "statement-" + customerId + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
