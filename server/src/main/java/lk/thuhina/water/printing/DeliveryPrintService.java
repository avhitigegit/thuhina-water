package lk.thuhina.water.printing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.dto.DeliveryDtos.BottleQty;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueBottle;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueCustomer;
import lk.thuhina.water.delivery.dto.DeliveryDtos.DueList;
import lk.thuhina.water.delivery.dto.DeliveryDtos.PlanRoute;
import lk.thuhina.water.delivery.dto.DeliveryDtos.PlanRow;
import lk.thuhina.water.delivery.dto.DeliveryDtos.Planning;
import lk.thuhina.water.delivery.service.PlanningService;
import lk.thuhina.water.delivery.service.ScheduleService;
import lk.thuhina.water.delivery.service.ScheduleService.BottleInfo;
import lk.thuhina.water.security.AuthUser;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery sheet (FR-44) and load sheet (FR-58), design 10: A4 landscape, customers grouped by area with blank columns for
 * the driver to write the paper bill number, filled, empties and cash / note; signature boxes at the end.
 */
@Service
public class DeliveryPrintService {

    static final String NOTE = "Not home? Phone the customer and leave the bottles only if they agree – write a note. "
            + "Fewer empties for any other reason – write the reason.";
    private static final float WRITE_HEIGHT = 24;

    private final ScheduleService schedule;
    private final PlanningService planning;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public DeliveryPrintService(ScheduleService schedule, PlanningService planning, CompanyHeaderService company, PdfRenderer renderer) {
        this.schedule = schedule;
        this.planning = planning;
        this.company = company;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public byte[] deliverySheet(LocalDate date, Long areaId) {
        return renderer.render(company.current(), deliverySheetDocument(date, areaId));
    }

    @Transactional(readOnly = true)
    public byte[] loadSheet(LocalDate date, Long areaId) {
        return renderer.render(company.current(), loadSheetDocument(date, areaId));
    }

    /** The delivery sheet for a date (default today): every customer on the Daily Delivery List, by area. */
    @Transactional(readOnly = true)
    public PrintDocument deliverySheetDocument(LocalDate date, Long areaId) {
        DueList list = schedule.dueList(date, areaId);
        String driver = CurrentUser.get().filter(u -> u.role() == Role.DELIVERY_STAFF).map(AuthUser::fullName).orElse("________________");
        List<String> meta = List.of(list.dayName() + " " + BusinessDates.format(list.date()), "Driver: " + driver,
                list.items().size() + " customers");
        List<List<String>> rows = new ArrayList<>();
        String area = null;
        int n = 0;
        for (DueCustomer c : list.items()) {
            if (!c.areaName().equals(area)) {
                area = c.areaName();
                rows.add(List.of(area));
            }
            String who = c.name() + (c.overdue() ? " (missed " + BusinessDates.format(c.nextDeliveryDate()) + ")" : "") + "\n" + c.address()
                    + (c.notes() == null || c.notes().isBlank() ? "" : "\n" + c.notes());
            String held = text(c.bottles(), true);
            rows.add(List.of(String.valueOf(++n), who, c.phone(), c.payTypeLabel(), held.isEmpty() ? "NEW" : held, text(c.bottles(), false),
                    blankZero(c.bottles().stream().mapToInt(DueBottle::toCollect).sum()),
                    blankZero(c.bottles().stream().mapToInt(DueBottle::owed).sum()), money(c.balance()), "", "", "", ""));
        }
        if (rows.isEmpty()) {
            rows.add(List.of("No customers due."));
        }
        PrintDocument.Table table = new PrintDocument.Table(
                List.of("#", "Customer / address", "Phone", "Pays", "Held", "Usual", "Collect", "Owed", "Balance", "Bill no.", "Filled",
                        "Empties", "Cash / note"),
                new boolean[] {false, false, false, false, false, false, true, true, true, false, false, false, false}, rows,
                new float[] {0.6f, 4.2f, 2f, 1.6f, 2.2f, 2.2f, 1.1f, 1f, 1.4f, 1.6f, 1.3f, 1.5f, 2.6f}, WRITE_HEIGHT);
        return new PrintDocument("DELIVERY SHEET", meta, List.of(), null, List.of(table), List.of(), List.of(NOTE),
                List.of("Driver", "Checked by"), PrintDocument.Page.A4_LANDSCAPE);
    }

    /** The load sheet for a date (default tomorrow): route totals, then the customers due and not yet entered by route. */
    @Transactional(readOnly = true)
    public PrintDocument loadSheetDocument(LocalDate date, Long areaId) {
        Planning p = planning.planning(date, areaId, PlanningService.SHOW_DUE);
        List<BottleInfo> bottles = schedule.activeBottles();
        int customers = p.routes().stream().mapToInt(PlanRoute::toLoad).sum();
        List<BottleQty> total = bottles.stream().map(b -> new BottleQty(b.code(), b.label(), p.routes().stream()
                .flatMap(r -> r.load().stream()).filter(q -> q.bottleTypeCode().equals(b.code())).mapToInt(BottleQty::qty).sum())).toList();
        String load = PlanningService.text(total);
        List<String> meta = List.of(p.dayName() + " " + BusinessDates.format(p.date()), customers + " customers · load "
                + (load.isEmpty() ? "–" : load));

        // Route totals – one column for each bottle type loaded on the date.
        List<BottleInfo> loaded = bottles.stream().filter(b -> total.stream().anyMatch(q -> q.bottleTypeCode().equals(b.code()) && q.qty() > 0))
                .toList();
        List<String> headers = new ArrayList<>(List.of("Route (area)", "Customers"));
        loaded.forEach(b -> headers.add(b.label() + " to load"));
        headers.add("Empties to collect");
        boolean[] numeric = new boolean[headers.size()];
        float[] widths = new float[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            numeric[i] = i > 0;
            widths[i] = i == 0 ? 4 : 1.6f;
        }
        List<List<String>> routeRows = new ArrayList<>();
        for (PlanRoute r : p.routes()) {
            if (r.toLoad() == 0) {
                continue;
            }
            List<String> row = new ArrayList<>(List.of(r.areaName(), String.valueOf(r.toLoad())));
            for (BottleInfo b : loaded) {
                row.add(String.valueOf(r.load().stream().filter(q -> q.bottleTypeCode().equals(b.code())).mapToInt(BottleQty::qty).sum()));
            }
            row.add(String.valueOf(r.toCollect()));
            routeRows.add(row);
        }
        if (routeRows.isEmpty()) {
            routeRows.add(List.of("No customers to deliver."));
        }
        PrintDocument.Table routes = new PrintDocument.Table(headers, numeric, routeRows, widths, 0);

        // Customers by route.
        List<List<String>> rows = new ArrayList<>();
        int n = 0;
        for (PlanRoute r : p.routes()) {
            List<PlanRow> todo = r.rows().stream().filter(x -> x.due() && !x.entered()).toList();
            if (todo.isEmpty()) {
                continue;
            }
            rows.add(List.of(r.areaName()));
            for (PlanRow x : todo) {
                rows.add(List.of(String.valueOf(++n), x.name() + "\n" + x.address(), x.phone(), x.payTypeLabel(), PlanningService.text(x.thisDate()),
                        blankZero(x.toCollect()), blankZero(x.owed()), money(x.balance()), "", "", "", ""));
            }
        }
        if (rows.isEmpty()) {
            rows.add(List.of("No customers to deliver."));
        }
        PrintDocument.Table list = new PrintDocument.Table(
                List.of("#", "Customer / address", "Phone", "Pays", "Usual", "Collect", "Owed", "Balance", "Bill no.", "Filled", "Empties",
                        "Cash / note"),
                new boolean[] {false, false, false, false, false, true, true, true, false, false, false, false}, rows,
                new float[] {0.6f, 4.4f, 2f, 1.6f, 2.4f, 1.1f, 1f, 1.4f, 1.6f, 1.3f, 1.5f, 2.6f}, WRITE_HEIGHT);
        return new PrintDocument("LOAD SHEET", meta, List.of(), null, List.of(routes, list), List.of(), List.of(),
                List.of("Loaded by", "Driver", "Checked by"), PrintDocument.Page.A4_LANDSCAPE);
    }

    /** "2 × 20L, 1 × 10L" of the bottles held ({@code held}) or the usual quantities. */
    private static String text(List<DueBottle> bottles, boolean held) {
        return String.join(", ", bottles.stream().filter(b -> (held ? b.held() : b.usual()) > 0)
                .map(b -> (held ? b.held() : b.usual()) + " × " + b.label()).toList());
    }

    private static String blankZero(int n) {
        return n == 0 ? "" : String.valueOf(n);
    }

    /** A positive balance in whole rupees (1,350); blank when nothing is owed. */
    static String money(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            return "";
        }
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v.setScale(0, RoundingMode.HALF_UP));
    }
}
