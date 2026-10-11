package lk.thuhina.water.printing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Printed documents as PDF (design 7.2 Print). More documents are added by later modules. */
@Tag(name = "Print")
@RestController
@RequestMapping("/print")
public class PrintController {

    private final BillPrintService bills;

    public PrintController(BillPrintService bills) {
        this.bills = bills;
    }

    @Operation(summary = "Bill PDF (A5) – opens in the browser to print")
    @GetMapping(value = "/bill/{txnId}", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('" + Permissions.SALES_VIEW + "')")
    public ResponseEntity<byte[]> bill(@PathVariable long txnId) {
        byte[] pdf = bills.bill(txnId);
        String billNo = bills.billNo(txnId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename("bill-" + billNo + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
