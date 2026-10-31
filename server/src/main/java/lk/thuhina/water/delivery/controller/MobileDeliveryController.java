package lk.thuhina.water.delivery.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentSaved;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.MobileDtos.MobilePaymentRequest;
import lk.thuhina.water.delivery.dto.MobileDtos.MyDay;
import lk.thuhina.water.delivery.service.MobileDeliveryService;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.sales.dto.SalesDtos.BillRequest;
import lk.thuhina.water.sales.dto.SalesDtos.SalePreview;
import lk.thuhina.water.sales.dto.SalesDtos.SaleResult;
import lk.thuhina.water.sales.service.SalesService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * My Deliveries on the phone (M17) for Delivery Staff (and the Admin): today's customers come from {@code GET /delivery/due};
 * here the delivery person finds another customer, saves today's bill (filled bottles left, empties collected, money received
 * and how) or money without a delivery, and sees their day. Always today; a sale over the credit limit is refused (CREDIT_LIMIT)
 * – only the office can approve it.
 */
@Tag(name = "My Deliveries")
@RestController
@RequestMapping("/delivery/mobile")
public class MobileDeliveryController {

    private static final String ENTRY = "hasAuthority('" + Permissions.DELIVERY_ENTRY + "')";
    private static final int SEARCH_LIMIT = 20;

    private final ScheduleService schedule;
    private final SalesService sales;
    private final MobileDeliveryService mobile;

    public MobileDeliveryController(ScheduleService schedule, SalesService sales, MobileDeliveryService mobile) {
        this.schedule = schedule;
        this.sales = sales;
        this.mobile = mobile;
    }

    @Operation(summary = "Active customers matching the text (code, name, phone or address; at least 2 characters), with today's "
            + "bottles, prices and bills – at most 20")
    @GetMapping("/customers")
    @PreAuthorize(ENTRY)
    public List<DueCustomer> customers(@RequestParam(required = false) String q) {
        return schedule.find(q, SEARCH_LIMIT);
    }

    @Operation(summary = "Today's bill without saving: amount, bottles after, credit check and problems")
    @PostMapping("/bills/preview")
    @PreAuthorize(ENTRY)
    public SalePreview billPreview(@RequestBody BillRequest request) {
        return sales.previewMobileBill(request);
    }

    @Operation(summary = "Save today's Exchange or Left at door bill (date is today, paper bill number optional); moves the next "
            + "delivery on")
    @PostMapping("/bills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ENTRY)
    public SaleResult bill(@Valid @RequestBody BillRequest request) {
        return sales.postMobileBill(request);
    }

    @Operation(summary = "Money received without a delivery – a payment (receipt) for today")
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(ENTRY)
    public PaymentSaved payment(@Valid @RequestBody MobilePaymentRequest request) {
        return mobile.receive(request);
    }

    @Operation(summary = "The signed-in user's day: bills and payments saved today, bottles left / collected and money by method")
    @GetMapping("/day")
    @PreAuthorize(ENTRY)
    public MyDay day() {
        return mobile.myDay();
    }
}
