package lk.thuhina.water.printing;

import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoLineView;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierInfo;
import lk.thuhina.water.purchasing.service.PurchaseOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase order PDF (design 10, prototype purchasing.html printPO): A4 portrait, PO number and dates, supplier with their
 * quotation reference and terms, lines, total, prepared / approved by and supplier acceptance. A Draft or Cancelled PO
 * carries a large DRAFT / CANCELLED watermark and "not valid for supply".
 */
@Service
public class PurchaseOrderPrintService {

    private static final boolean[] NUMERIC = {false, false, true, true, true};
    private static final float[] WIDTHS = {0.5f, 5f, 1.2f, 1.8f, 2f};

    private final PurchaseOrderService orders;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public PurchaseOrderPrintService(PurchaseOrderService orders, CompanyHeaderService company, PdfRenderer renderer) {
        this.orders = orders;
        this.company = company;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] purchaseOrder(long id) {
        return renderer.render(company.current(), document(orders.detail(id)));
    }

    @Transactional(readOnly = true)
    public String poNo(long id) {
        return orders.detail(id).poNo();
    }

    /** The PO as plain parts (also used by tests to check the wording). */
    public PrintDocument document(PoDetail po) {
        List<String> meta = List.of(po.poNo(), "Date: " + BusinessDates.format(po.date()), "Expected: " + BusinessDates.format(po.expectedDate()));
        SupplierInfo s = po.supplier();
        List<String> party = new ArrayList<>();
        party.add(s.name());
        if (s.address() != null) {
            party.add(s.address());
        }
        party.add((s.contact() == null ? "" : "Attn: " + s.contact() + " · ") + s.phone());
        party.add("Your quotation: " + (po.supplierRef() == null ? po.quoteNo() : po.supplierRef()) + " · Terms: " + s.termsLabel());

        String watermark = switch (po.status()) {
            case "DRAFT" -> "DRAFT";
            case "CANCELLED" -> "CANCELLED";
            default -> null;
        };
        String subtitle = watermark == null ? "From quotation request " + po.qrNo()
                : watermark + " – not valid for supply" + (po.cancelReason() == null ? "" : ". Reason: " + po.cancelReason());

        List<List<String>> rows = new ArrayList<>();
        int n = 1;
        for (PoLineView l : po.lines()) {
            rows.add(List.of(String.valueOf(n++), l.name(), String.valueOf(l.qty()), BillPrintService.number(l.unitPrice()),
                    BillPrintService.number(l.amount())));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("#", "Description", "Qty", "Unit price", "Amount (Rs.)"), NUMERIC, rows,
                WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Total", Money.format(po.total()), true));
        List<String> footer = new ArrayList<>();
        if (po.notes() != null) {
            footer.add("Notes: " + po.notes());
        }
        footer.add("Please deliver by " + BusinessDates.format(po.expectedDate()) + " and quote " + po.poNo() + " on your delivery note and invoice.");
        List<String> signatures = List.of("Prepared by\n" + po.createdByName(),
                "Approved by\n" + (po.approvedByName() == null ? "" : po.approvedByName()), "Supplier acceptance");
        return new PrintDocument("PURCHASE ORDER", meta, party, subtitle, List.of(table), summary, footer, signatures,
                PrintDocument.Page.A4_PORTRAIT, watermark);
    }
}
