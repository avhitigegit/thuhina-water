package lk.thuhina.water.reports.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.reports.dto.ReportDtos.ReportInfo;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.security.Permissions;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reports (BRD section 8, design 6 Reports): Admin (reports.all) runs all ten; the Accountant (reports.view only) runs
 * Sales, Purchasing, Outstanding & aging, Profit and Expenses. Range default: the start of last month to today; at most a year.
 */
@Service
public class ReportService {

    public static final String MSG_RANGE = "The From date must be on or before the To date.";
    public static final String MSG_RANGE_LONG = "Choose a range of at most 366 days.";
    static final int MAX_DAYS = 366;

    /** The reports the Accountant may run (design 8.2). */
    static final Set<String> ACCOUNTANT = Set.of("sales", "purchasing", "outstanding", "profit", "expenses");

    private record Report(String name, BiFunction<LocalDate, LocalDate, ReportView> build) {
    }

    private final Map<String, Report> reports = new LinkedHashMap<>();
    private final Clock clock;

    public ReportService(SalesReports sales, StockReports stock, FinanceReports finance, Clock clock) {
        this.clock = clock;
        reports.put("sales", new Report("Sales", sales::sales));
        reports.put("purchasing", new Report("Purchasing", finance::purchasing));
        reports.put("production", new Report("Production", stock::production));
        reports.put("stock", new Report("Stock", stock::stock));
        reports.put("movement", new Report("Bottle movement", sales::movement));
        reports.put("damage", new Report("Damage and write-off", stock::damage));
        reports.put("outstanding", new Report("Outstanding and aging", finance::outstanding));
        reports.put("profit", new Report("Profit", finance::profit));
        reports.put("expenses", new Report("Expenses", finance::expenses));
        reports.put("delivery", new Report("Delivery", sales::delivery));
    }

    /** The reports the user may run, in the menu order. */
    public List<ReportInfo> available() {
        Set<String> perms = permissions();
        return reports.entrySet().stream().filter(e -> allowed(perms, e.getKey()))
                .map(e -> new ReportInfo(e.getKey(), e.getValue().name())).toList();
    }

    @Transactional(readOnly = true)
    public ReportView run(String key, LocalDate from, LocalDate to) {
        Report r = reports.get(key);
        if (r == null) {
            throw new NotFoundException("Report", key);
        }
        if (!allowed(permissions(), key)) {
            throw new AccessDeniedException("The " + r.name() + " report needs the Admin");
        }
        LocalDate today = BusinessDates.today(clock);
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? today.minusMonths(1).withDayOfMonth(1) : from;
        if (f.isAfter(t)) {
            throw new ValidationException("from", MSG_RANGE);
        }
        if (ChronoUnit.DAYS.between(f, t) >= MAX_DAYS) {
            throw new ValidationException("from", MSG_RANGE_LONG);
        }
        return r.build().apply(f, t);
    }

    static boolean allowed(Set<String> perms, String key) {
        return perms.contains(Permissions.REPORTS_ALL) || perms.contains(Permissions.REPORTS_VIEW) && ACCOUNTANT.contains(key);
    }

    private static Set<String> permissions() {
        return CurrentUser.get().map(u -> u.permissions()).orElse(Set.of());
    }
}
