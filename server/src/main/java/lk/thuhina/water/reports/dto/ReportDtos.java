package lk.thuhina.water.reports.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reports (design 6 Reports, 7.2): one shape for every report, used by the screen and the PDF / Excel exports. Values are
 * plain text so nothing is lost: MONEY and NUMBER as "1350.00" / "12", DATE as ISO "2026-09-30", TEXT as is; empty = "".
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** A report the user may run: key (sales, purchasing, …) and name. */
    public record ReportInfo(String key, String name) {
    }

    /** TEXT, NUMBER, MONEY or DATE – how the screen and the exports show the column. */
    public record ReportColumn(String header, String type) {
    }

    /** {@code bold} rows (totals inside a table, e.g. Gross profit) are shown in bold. */
    public record ReportTable(String title, List<ReportColumn> columns, List<List<String>> rows,
                              @Schema(nullable = true) List<String> footer, List<Integer> boldRows) {
    }

    /** A KPI: label, value and its type (MONEY, NUMBER or TEXT); {@code alert} shows it in red. */
    public record ReportKpi(String label, String value, String type, boolean alert) {
    }

    /** A bar of a chart: label under the bar, value and the hover text. */
    public record ChartPoint(String label, String value, String title) {
    }

    public record ReportChart(String title, List<ChartPoint> points) {
    }

    /** A report for a date range. */
    public record ReportView(String key, String name, LocalDate from, LocalDate to, List<ReportKpi> kpis,
                             @Schema(nullable = true) ReportChart chart, List<ReportTable> tables, @Schema(nullable = true) String note) {
    }
}
