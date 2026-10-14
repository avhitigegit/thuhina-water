package lk.thuhina.water.billing.controller;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.billing.dto.BillingDtos.AccountListResponse;
import lk.thuhina.water.billing.dto.BillingDtos.AccountSummary;
import lk.thuhina.water.billing.dto.BillingDtos.AccountView;
import lk.thuhina.water.billing.dto.BillingDtos.GenerateRequest;
import lk.thuhina.water.billing.dto.BillingDtos.GenerateResult;
import lk.thuhina.water.billing.dto.BillingDtos.InvoicePreview;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceRow;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceView;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentListResponse;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRequest;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRow;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentSaved;
import lk.thuhina.water.billing.dto.BillingDtos.RecentMoney;
import lk.thuhina.water.billing.dto.BillingDtos.StatementView;
import lk.thuhina.water.billing.service.AgingService;
import lk.thuhina.water.billing.service.BillingQueryService;
import lk.thuhina.water.billing.service.InvoiceService;
import lk.thuhina.water.billing.service.PaymentService;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.security.Permissions;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Billing & payments (design 7.2 Billing). Admin and Accountant full; Delivery Staff none. */
@Tag(name = "Billing & payments")
@RestController
public class BillingController {

    private static final String VIEW = "hasAuthority('" + Permissions.BILLING_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.BILLING_EDIT + "')";

    private final AgingService aging;
    private final BillingQueryService query;
    private final PaymentService payments;
    private final InvoiceService invoices;

    public BillingController(AgingService aging, BillingQueryService query, PaymentService payments, InvoiceService invoices) {
        this.aging = aging;
        this.query = query;
        this.payments = payments;
        this.invoices = invoices;
    }

    // ------------------------------------------------------------------ customer accounts

    @Operation(summary = "Customer accounts with a balance and their aging (0–30 / 31–60 / 61–90 / 90+), largest first, with the "
            + "totals of every matching row. show = over (over the credit limit) / old (older than 30 days); asOf default today")
    @GetMapping("/accounts")
    @PreAuthorize(VIEW)
    public AccountListResponse accounts(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) PayType pay,
                                        @RequestParam(required = false) String show,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return aging.accounts(new AgingService.Filter(q, pay, show), asOf, page, size);
    }

    @Operation(summary = "Page KPIs: total outstanding and each aging bucket over every customer with a balance")
    @GetMapping("/accounts/summary")
    @PreAuthorize(VIEW)
    public AccountSummary summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return aging.summary(asOf);
    }

    @Operation(summary = "One customer's account (also with a zero balance) and invoices – for the Receive payment pop-up")
    @GetMapping("/accounts/{customerId}")
    @PreAuthorize(VIEW)
    public AccountView account(@PathVariable long customerId) {
        return new AccountView(aging.account(customerId, null), query.invoiceLinks(customerId));
    }

    // ------------------------------------------------------------------ payments

    @Operation(summary = "Receive a payment (RC-00001). Reference required for Bank transfer and Cheque; part payments and advances allowed")
    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public PaymentSaved receive(@Valid @RequestBody PaymentRequest request) {
        return payments.receive(request);
    }

    @Operation(summary = "Payments newest first; customer and dates optional")
    @GetMapping("/payments")
    @PreAuthorize(VIEW)
    public PaymentListResponse payments(@RequestParam(required = false) Long customer,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return query.payments(customer, from, to, page, size);
    }

    @Operation(summary = "Recent payments and cash collected on bills, newest first (default 15)")
    @GetMapping("/payments/recent")
    @PreAuthorize(VIEW)
    public List<RecentMoney> recent(@RequestParam(defaultValue = "15") int limit) {
        return query.recent(limit);
    }

    @Operation(summary = "One payment")
    @GetMapping("/payments/{id}")
    @PreAuthorize(VIEW)
    public PaymentRow payment(@PathVariable long id) {
        return query.payment(id);
    }

    // ------------------------------------------------------------------ monthly invoices

    @Operation(summary = "Monthly invoices tab: every Monthly bill customer for the month (default last month) with deliveries, "
            + "this month, previous balance, payments, total due and the invoice when generated; months offered = this and 3 before")
    @GetMapping("/invoices/preview")
    @PreAuthorize(VIEW)
    public InvoicePreview preview(@RequestParam(required = false) String period) {
        return query.preview(period);
    }

    @Operation(summary = "Generate invoices (INV-YYMM-0001) for Monthly bill customers without one for the month; skipped customers "
            + "come back with the reason")
    @PostMapping("/invoices/generate")
    @PreAuthorize(EDIT)
    public GenerateResult generate(@RequestBody GenerateRequest request) {
        return invoices.generate(request.period());
    }

    @Operation(summary = "Invoices newest first; month (YYYY-MM) and customer optional")
    @GetMapping("/invoices")
    @PreAuthorize(VIEW)
    public List<InvoiceRow> invoices(@RequestParam(required = false) String period, @RequestParam(required = false) Long customer) {
        return query.invoices(period, customer);
    }

    @Operation(summary = "An invoice with its lines")
    @GetMapping("/invoices/{id}")
    @PreAuthorize(VIEW)
    public InvoiceView invoice(@PathVariable long id) {
        return query.invoice(id);
    }

    // ------------------------------------------------------------------ statements

    @Operation(summary = "Statement of account: brought forward, charges and payments with the running balance, aging on the to date. "
            + "from default the start of last month, to default today")
    @GetMapping("/statements/{customerId}")
    @PreAuthorize(VIEW)
    public StatementView statement(@PathVariable long customerId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return query.statement(customerId, from, to);
    }
}
