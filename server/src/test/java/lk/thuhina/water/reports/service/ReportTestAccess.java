package lk.thuhina.water.reports.service;

import java.util.Set;

/** Test access to the report helpers that are package-private in the service package. */
public final class ReportTestAccess {

    private ReportTestAccess() {
    }

    public static boolean allowed(Set<String> permissions, String key) {
        return ReportService.allowed(permissions, key);
    }

    public static String shown(String value, String type) {
        return ReportExportService.shown(value, type);
    }

    public static String text(Object value, String type) {
        return ReportBuilder.text(value, type);
    }

    public static String sheetName(String title, Set<String> used) {
        return ReportExportService.sheetName(title, used);
    }

    public static String saleType(String kind) {
        return SalesReports.saleType(kind);
    }

    public static String location(String location) {
        return StockReports.location(location);
    }
}
