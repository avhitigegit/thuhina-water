package lk.thuhina.water.printing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.billing.dto.BillingDtos.CustomerInfo;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceLine;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceView;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRow;
import lk.thuhina.water.billing.dto.BillingDtos.StatementRow;
import lk.thuhina.water.billing.dto.BillingDtos.StatementView;
import lk.thuhina.water.billing.service.BillingQueryService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing prints (design 10, prototype billing.html): the payment receipt (A5 – receipt no, customer, amount, how paid, balance
 * before / now), the monthly invoice (A4 – a line per delivery, previous balance, payments, this month, total due, "VAT not
 * applied") and the statement of account (A4 – brought forward, running balance, aging line).
 */
@Service
public class BillingPrintService {

    private static final boolean[] RECEIPT_NUMERIC = {false, true};
    private static final float[] RECEIPT_WIDTHS = {2f, 3f};
    private static final boolean[] INVOICE_NUMERIC = {false, false, false, true, true, true};
    private static final float[] INVOICE_WIDTHS = {1.6f, 1.6f, 5f, 0.9f, 1.5f, 1.8f};
    private static final boolean[] STATEMENT_NUMERIC = {false, false, false, true, true, true};
    private static final float[] STATEMENT_WIDTHS = {1.6f, 1.7f, 5f, 1.6f, 1.6f, 1.8f};

    private final BillingQueryService billing;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public BillingPrintService(BillingQueryService billing, CompanyHeaderService company, PdfRenderer renderer) {
        this.billing = billing;
        this.company = company;
        this.renderer = renderer;
    }

    // ------------------------------------------------------------------ receipt

    @Transactional(readOnly = true)
    public byte[] receipt(long paymentId) {
        return renderer.render(company.current(), receiptDocument(billing.payment(paymentId)));
    }

    @Transactional(readOnly = true)
    public String receiptNo(long paymentId) {
        return billing.payment(paymentId).receiptNo();
    }

    /** The receipt as plain parts (also used by tests to check the wording). */
    @Transactional(readOnly = true)
    public PrintDocument receiptDocument(PaymentRow p) {
        CustomerInfo c = billing.customerInfo(p.customer().id());
        String how = p.methodLabel() + (p.reference() == null ? "" : " – " + p.reference());
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Amount received", Money.format(p.amount())));
        rows.add(List.of("Paid by", how));
        if (p.bank() != null || p.chequeDate() != null) {
            rows.add(List.of("Cheque", String.join(" · ", java.util.stream.Stream.of(p.bank(),
                    p.chequeDate() == null ? null : "dated " + BusinessDates.format(p.chequeDate())).filter(x -> x != null).toList())));
        }
        if (p.invoiceNo() != null) {
            rows.add(List.of("Against invoice", p.invoiceNo()));
        }
        rows.add(List.of("Balance before", Money.format(p.balanceBefore())));
        PrintDocument.Table table = new PrintDocument.Table(List.of("Payment", ""), RECEIPT_NUMERIC, rows, RECEIPT_WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Balance now", Money.format(p.balanceAfter()), true));
        List<String> footer = new ArrayList<>();
        if (p.partPayment()) {
            footer.add("Part payment.");
        } else if (p.balanceAfter().signum() < 0) {
            footer.add("Paid in advance – " + Money.format(p.balanceAfter().negate()) + " credit balance.");
        }
        if (p.note() != null) {
            footer.add("Note: " + p.note());
        }
        return new PrintDocument("RECEIPT", List.of(p.receiptNo(), BusinessDates.format(p.date())), party(c, false),
                "Received with thanks", List.of(table), summary, footer, List.of("Received by\n" + p.createdByName()),
                PrintDocument.Page.A5_PORTRAIT);
    }

    // ------------------------------------------------------------------ invoice

    @Transactional(readOnly = true)
    public byte[] invoice(long invoiceId) {
        return renderer.render(company.current(), invoiceDocument(billing.invoice(invoiceId)));
    }

    @Transactional(readOnly = true)
    public String invoiceNo(long invoiceId) {
        return billing.invoiceRow(invoiceId).invoiceNo();
    }

    /** The invoice as plain parts (also used by tests to check the wording). */
    public PrintDocument invoiceDocument(InvoiceView inv) {
        List<String> meta = List.of(inv.invoiceNo(), "Date: " + BusinessDates.format(inv.invoiceDate()),
                "Period: " + BusinessDates.format(inv.from()) + " – " + BusinessDates.format(inv.to()), "Due: " + BusinessDates.format(inv.dueDate()));
        List<List<String>> rows = new ArrayList<>();
        for (InvoiceLine l : inv.lines()) {
            rows.add(List.of(BusinessDates.format(l.date()), l.billNo(), l.description(), l.qty() == null ? "" : String.valueOf(l.qty()),
                    l.unitPrice() == null ? "" : BillPrintService.number(l.unitPrice()), BillPrintService.number(l.amount())));
        }
        if (rows.isEmpty()) {
            rows.add(List.of("", "", "No deliveries this month", "", "", ""));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("Date", "Bill", "Description", "Qty", "Unit price", "Amount (Rs.)"),
                INVOICE_NUMERIC, rows, INVOICE_WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(
                new PrintDocument.SummaryRow("Previous balance", Money.format(inv.opening()), false),
                new PrintDocument.SummaryRow("Payments received", "– " + Money.format(inv.payments()), false),
                new PrintDocument.SummaryRow("This month", Money.format(inv.amount()), false),
                new PrintDocument.SummaryRow("Total due by " + BusinessDates.format(inv.dueDate()), Money.format(inv.totalDue()), true));
        List<String> footer = List.of("VAT not applied (to be confirmed). Please quote " + inv.invoiceNo() + " with your payment.");
        List<String> party = new ArrayList<>(List.of("Bill to: " + inv.customer().name() + " (" + inv.customer().code() + ")"));
        party.addAll(party(inv.customer(), false).subList(1, 3));
        return new PrintDocument("INVOICE", meta, party, inv.periodLabel() + " – " + inv.deliveries() + " deliver"
                + (inv.deliveries() == 1 ? "y" : "ies"), List.of(table), summary, footer, List.of(), PrintDocument.Page.A4_PORTRAIT);
    }

    // ------------------------------------------------------------------ statement

    @Transactional(readOnly = true)
    public byte[] statement(long customerId, LocalDate from, LocalDate to) {
        return renderer.render(company.current(), statementDocument(billing.statement(customerId, from, to)));
    }

    /** The statement as plain parts (also used by tests to check the wording). */
    public PrintDocument statementDocument(StatementView s) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of(BusinessDates.format(s.from()), "", "Brought forward", "", "", BillPrintService.number(s.broughtForward())));
        for (StatementRow r : s.rows()) {
            rows.add(List.of(BusinessDates.format(r.date()), r.ref(), r.description(), amount(r.charges()), amount(r.payments()),
                    BillPrintService.number(r.balance())));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("Date", "Ref", "Description", "Charges", "Payments", "Balance"),
                STATEMENT_NUMERIC, rows, STATEMENT_WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Balance due", Money.format(s.closing()), true));
        List<String> footer = List.of("Age of balance: 0–30 " + Money.format(s.days0to30()) + " · 31–60 " + Money.format(s.days31to60())
                + " · 61–90 " + Money.format(s.days61to90()) + " · 90+ " + Money.format(s.over90()), "Bottle deposits are not refundable.");
        return new PrintDocument("STATEMENT", List.of(BusinessDates.format(s.from()) + " – " + BusinessDates.format(s.to())),
                party(s.customer(), true), "Statement of account", List.of(table), summary, footer, List.of(), PrintDocument.Page.A4_PORTRAIT);
    }

    // ------------------------------------------------------------------ helpers

    /** Name (code), address and area, phone (+ pay type). */
    private static List<String> party(CustomerInfo c, boolean payType) {
        return List.of(c.name() + " (" + c.code() + ")", c.address() + ", " + c.area(), c.phone() + (payType ? " · " + c.payTypeLabel() : ""));
    }

    private static String amount(BigDecimal v) {
        return v == null || v.signum() == 0 ? "" : BillPrintService.number(v);
    }
}
