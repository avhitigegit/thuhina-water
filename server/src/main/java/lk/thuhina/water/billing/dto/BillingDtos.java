package lk.thuhina.water.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.PayMethod;

/** Billing & payments (design 6 Billing, 7.2): customer accounts with aging, payments, recent money, invoices, statements. */
public final class BillingDtos {

    private BillingDtos() {
    }

    public record CustomerLink(long id, String code, String name) {
    }

    /** The customer on a printed document. */
    public record CustomerInfo(long id, String code, String name, String address, String area, String phone, String payType,
                               String payTypeLabel, int termsDays) {
    }

    // ------------------------------------------------------------------ customer accounts (FR-48, FR-51)

    /**
     * One customer's account: aging buckets (credits settle the oldest bills first) and the balance (negative = advance).
     * {@code overLimit} = Credit / Monthly bill and balance &gt; credit limit; {@code oldest} = date of the oldest unpaid bill.
     */
    public record AccountRow(
            long customerId,
            String code,
            String name,
            String phone,
            String payType,
            String payTypeLabel,
            BigDecimal creditLimit,
            String status,
            BigDecimal days0to30,
            BigDecimal days31to60,
            BigDecimal days61to90,
            BigDecimal over90,
            BigDecimal balance,
            boolean overLimit,
            @Schema(nullable = true) LocalDate oldest) {
    }

    public record AgingTotals(BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90, BigDecimal balance) {
    }

    /** A page of accounts (largest balance first) with the totals of every row that matches the filters. */
    public record AccountListResponse(List<AccountRow> items, int page, int size, long total, AgingTotals totals) {
    }

    /** Page KPIs over every customer with a balance: total outstanding and each bucket. */
    public record AccountSummary(BigDecimal totalOutstanding, BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90,
                                 BigDecimal over90, long customers, long overLimit, LocalDate asOf) {
    }

    /** One customer's account (also with a zero balance) and their invoices, newest first – for the Receive payment pop-up. */
    public record AccountView(AccountRow account, List<InvoiceLink> invoices) {
    }

    // ------------------------------------------------------------------ payments (FR-50)

    public record CustomerPaymentRequest(
            Long customerId,
            @NotNull(message = "Enter the date.") LocalDate date,
            BigDecimal amount,
            PayMethod method,
            @Size(max = 100, message = "Reference: at most 100 characters.") String reference,
            @Size(max = 100, message = "Bank: at most 100 characters.") String bank,
            LocalDate chequeDate,
            Long invoiceId,
            @Size(max = 200, message = "Note: at most 200 characters.") String note) {
    }

    /** A payment with its receipt number; {@code balanceAfter} = balance before − amount. */
    public record CustomerPaymentRow(
            long id,
            String receiptNo,
            LocalDate date,
            CustomerLink customer,
            BigDecimal amount,
            String method,
            String methodLabel,
            @Schema(nullable = true) String reference,
            @Schema(nullable = true) String bank,
            @Schema(nullable = true) LocalDate chequeDate,
            @Schema(nullable = true) Long invoiceId,
            @Schema(nullable = true) String invoiceNo,
            @Schema(nullable = true) String note,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter,
            boolean partPayment,
            String createdByName,
            Instant createdAt) {
    }

    /** After saving: the payment and "Payment saved – Rs. 2,000.00 received from …. Balance now …". */
    public record PaymentSaved(CustomerPaymentRow payment, BigDecimal balanceNow, boolean partPayment, boolean advance, String message) {
    }

    public record PaymentListResponse(List<CustomerPaymentRow> items, int page, int size, long total) {
    }

    /** Recent money in (FR-47): a payment (kind PAYMENT, ref RC-…) or cash paid on a bill (kind BILL, ref B…). */
    public record RecentMoney(
            String kind,
            long id,
            String ref,
            LocalDate date,
            Instant at,
            @Schema(nullable = true) Long customerId,
            String customerName,
            String how,
            BigDecimal amount) {
    }

    // ------------------------------------------------------------------ monthly invoices (FR-49)

    public record InvoiceLink(long id, String invoiceNo, String period, String periodLabel, BigDecimal totalDue) {
    }

    public record PeriodOption(String period, String label) {
    }

    /**
     * A Monthly bill customer in the chosen month: deliveries (bills in the month), this month (Σ amount − paid), previous
     * balance, payments in the month, total due (the invoice's when generated), and the invoice when there is one.
     */
    public record InvoiceMonthRow(
            long customerId,
            String code,
            String name,
            String status,
            int deliveries,
            BigDecimal charges,
            BigDecimal paidOnBills,
            BigDecimal thisMonth,
            BigDecimal opening,
            BigDecimal payments,
            BigDecimal totalDue,
            @Schema(nullable = true) InvoiceLink invoice) {
    }

    /** The Monthly invoices tab for a month; {@code toGenerate} = customers without an invoice yet. */
    public record InvoicePreview(String period, String periodLabel, List<PeriodOption> periods, List<InvoiceMonthRow> rows,
                                 int toGenerate) {
    }

    public record GenerateRequest(String period) {
    }

    public record SkippedCustomer(long customerId, String code, String name, String reason) {
    }

    public record InvoiceRow(
            long id,
            String invoiceNo,
            CustomerLink customer,
            String period,
            String periodLabel,
            LocalDate invoiceDate,
            LocalDate dueDate,
            int deliveries,
            BigDecimal opening,
            BigDecimal payments,
            BigDecimal amount,
            BigDecimal totalDue,
            BigDecimal paid,
            String createdByName) {
    }

    /** Generated invoices and the customers skipped, with the reason. */
    public record GenerateResult(String period, String periodLabel, List<InvoiceRow> created, List<SkippedCustomer> skipped) {
    }

    /** A line of the invoice: a delivery, deposit, damage replacement or product (qty and price) or "Paid on delivery". */
    public record InvoiceLine(LocalDate date, String billNo, String description, @Schema(nullable = true) Integer qty,
                              @Schema(nullable = true) BigDecimal unitPrice, BigDecimal amount) {
    }

    public record InvoiceView(
            long id,
            String invoiceNo,
            CustomerInfo customer,
            String period,
            String periodLabel,
            LocalDate from,
            LocalDate to,
            LocalDate invoiceDate,
            LocalDate dueDate,
            int deliveries,
            BigDecimal opening,
            BigDecimal charges,
            BigDecimal paidOnBills,
            BigDecimal payments,
            BigDecimal amount,
            BigDecimal totalDue,
            BigDecimal paid,
            List<InvoiceLine> lines,
            String createdByName) {
    }

    // ------------------------------------------------------------------ statement (FR-52)

    public record StatementRow(LocalDate date, String ref, String description, BigDecimal charges, BigDecimal payments,
                               BigDecimal balance) {
    }

    /** Brought forward, each charge / payment with the running balance, the balance due and its aging on the "to" date. */
    public record StatementView(CustomerInfo customer, LocalDate from, LocalDate to, BigDecimal broughtForward, List<StatementRow> rows,
                                BigDecimal closing, BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90,
                                BigDecimal over90) {
    }
}
