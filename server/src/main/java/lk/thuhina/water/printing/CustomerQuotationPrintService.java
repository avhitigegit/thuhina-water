package lk.thuhina.water.printing;

import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqLineView;
import lk.thuhina.water.quotations.dto.CustomerQuotationDtos.CqView;
import lk.thuhina.water.quotations.service.CustomerQuotationQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer quotation (design 10, prototype quotations.html printQ): QUOTATION, number, date, valid until; To (contact,
 * organisation, address, phone / email); subject; lines (description, qty, unit, unit price, amount); total; delivery,
 * payment terms and the numbered conditions; signatures (prepared by, authorised, customer acceptance). DRAFT watermark while
 * the quotation has not been sent.
 */
@Service
public class CustomerQuotationPrintService {

    private static final boolean[] NUMERIC = {false, false, true, false, true, true};
    private static final float[] WIDTHS = {0.5f, 5.5f, 1f, 1.4f, 1.6f, 1.8f};

    private final CustomerQuotationQueryService quotations;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public CustomerQuotationPrintService(CustomerQuotationQueryService quotations, CompanyHeaderService company, PdfRenderer renderer) {
        this.quotations = quotations;
        this.company = company;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] quotation(long id) {
        CompanyHeader co = company.current();
        return renderer.render(co, document(quotations.view(id), co));
    }

    @Transactional(readOnly = true)
    public String cqNo(long id) {
        return quotations.view(id).cqNo();
    }

    /** The quotation as plain parts (also used by tests to check the wording). */
    public PrintDocument document(CqView q, CompanyHeader co) {
        List<String> meta = List.of(q.cqNo(), "Date: " + BusinessDates.format(q.quoteDate()), "Valid until: " + BusinessDates.format(q.validUntil()));
        List<String> party = new ArrayList<>();
        party.add("To:");
        if (q.contact() != null) {
            party.add(q.contact() + (q.designation() == null ? "" : ", " + q.designation()));
        }
        party.add(q.orgName());
        if (q.address() != null) {
            party.add(q.address());
        }
        String phoneEmail = String.join(" · ", java.util.stream.Stream.of(q.phone(), q.email()).filter(x -> x != null).toList());
        if (!phoneEmail.isEmpty()) {
            party.add(phoneEmail);
        }
        List<List<String>> rows = new ArrayList<>();
        for (CqLineView l : q.lines()) {
            rows.add(List.of(String.valueOf(l.lineNo()), l.description(), String.valueOf(l.qty()), l.unit() == null ? "" : l.unit(),
                    BillPrintService.number(l.unitPrice()), BillPrintService.number(l.amount())));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("#", "Description", "Qty", "Unit", "Unit price", "Amount (Rs.)"),
                NUMERIC, rows, WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Total", Money.format(q.total()), true));
        List<String> footer = new ArrayList<>();
        if (q.deliveryText() != null) {
            footer.add("Delivery: " + q.deliveryText());
        }
        footer.add("Payment terms: " + q.termsLabel());
        if (q.conditions() != null) {
            footer.add("Terms and conditions");
            int n = 0;
            for (String c : q.conditions().split("\n")) {
                if (!c.isBlank()) {
                    footer.add(++n + ". " + c.trim());
                }
            }
        }
        footer.add("We look forward to serving you." + (co.phone() == null ? "" : " For any questions please call " + co.phone() + "."));
        String subtitle = (q.subject() == null ? "" : "Subject: " + q.subject() + "\n")
                + "Thank you for your enquiry. We are pleased to quote as follows:";
        return new PrintDocument("QUOTATION", meta, party, subtitle, List.of(table), summary, footer,
                List.of("Prepared by\n" + q.createdByName(), "Authorised signature\n" + co.name(), "Customer acceptance\n(signature & date)"),
                PrintDocument.Page.A4_PORTRAIT, "DRAFT".equals(q.status()) ? "DRAFT" : null);
    }
}
