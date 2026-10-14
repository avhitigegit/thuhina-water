package lk.thuhina.water.printing;

import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.production.dto.ProductionDtos.BatchView;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchView;
import lk.thuhina.water.production.service.ProductionQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatch note PDF (design 10, prototype production.html printDispatch): A4 portrait, dispatch no, date, vehicle, the
 * factory, one line per bottle type (batch no, empties sent), total, signatures (sent by, driver, received by factory).
 */
@Service
public class DispatchNotePrintService {

    private static final boolean[] NUMERIC = {false, false, false, true};
    private static final float[] WIDTHS = {0.5f, 5f, 2f, 2f};

    private final ProductionQueryService production;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public DispatchNotePrintService(ProductionQueryService production, CompanyHeaderService company, PdfRenderer renderer) {
        this.production = production;
        this.company = company;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] dispatchNote(String dispatchNo) {
        return renderer.render(company.current(), document(production.dispatch(dispatchNo)));
    }

    /** The dispatch note as plain parts (also used by tests to check the wording). */
    public PrintDocument document(DispatchView d) {
        List<String> meta = List.of(d.dispatchNo(), "Date: " + BusinessDates.format(d.date()),
                "Vehicle: " + (d.vehicleNo() == null ? "–" : d.vehicleNo()));
        List<String> party = new ArrayList<>();
        party.add(d.factory().name());
        if (d.factoryAddress() != null) {
            party.add(d.factoryAddress());
        }
        if (d.factoryContact() != null || d.factoryPhone() != null) {
            party.add("Attn: " + String.join(" · ", java.util.stream.Stream.of(d.factoryContact(), d.factoryPhone())
                    .filter(x -> x != null).toList()));
        }
        List<List<String>> rows = new ArrayList<>();
        int n = 1;
        for (BatchView b : d.batches()) {
            rows.add(List.of(String.valueOf(n++), "Empty " + b.bottleName(), b.batchNo(), String.valueOf(b.qtySent())));
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("#", "Description", "Batch", "Empty bottles"), NUMERIC, rows, WIDTHS, 0);
        List<PrintDocument.SummaryRow> summary = List.of(new PrintDocument.SummaryRow("Total bottles", String.valueOf(d.totalBottles()), true));
        List<String> footer = d.note() == null ? List.of() : List.of("Note: " + d.note());
        return new PrintDocument("DISPATCH NOTE", meta, party, "To filling factory – empties for filling", List.of(table), summary, footer,
                List.of("Sent by\n" + d.createdByName(), "Driver", "Received by (factory)"), PrintDocument.Page.A4_PORTRAIT);
    }
}
