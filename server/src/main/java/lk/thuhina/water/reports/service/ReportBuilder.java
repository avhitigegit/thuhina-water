package lk.thuhina.water.reports.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.reports.dto.ReportDtos.ChartPoint;
import lk.thuhina.water.reports.dto.ReportDtos.ReportChart;
import lk.thuhina.water.reports.dto.ReportDtos.ReportColumn;
import lk.thuhina.water.reports.dto.ReportDtos.ReportKpi;
import lk.thuhina.water.reports.dto.ReportDtos.ReportTable;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;

/**
 * Builds a {@link ReportView}: KPIs, an optional chart, tables of typed columns and a note. Values become plain text
 * (MONEY "1350.00", NUMBER "12", DATE "2026-09-30"), so the screen, the PDF and the Excel file show the same data.
 */
final class ReportBuilder {

    static final String TEXT = "TEXT";
    static final String NUMBER = "NUMBER";
    static final String MONEY = "MONEY";
    static final String DATE = "DATE";

    private final String key;
    private final String name;
    private final LocalDate from;
    private final LocalDate to;
    private final List<ReportKpi> kpis = new ArrayList<>();
    private final List<ReportTable> tables = new ArrayList<>();
    private ReportChart chart;
    private String note;

    ReportBuilder(String key, String name, LocalDate from, LocalDate to) {
        this.key = key;
        this.name = name;
        this.from = from;
        this.to = to;
    }

    ReportBuilder money(String label, BigDecimal value) {
        kpis.add(new ReportKpi(label, text(value, MONEY), MONEY, false));
        return this;
    }

    ReportBuilder number(String label, long value) {
        kpis.add(new ReportKpi(label, String.valueOf(value), NUMBER, false));
        return this;
    }

    ReportBuilder kpi(ReportKpi k) {
        kpis.add(k);
        return this;
    }

    ReportBuilder chart(String title, List<ChartPoint> points) {
        this.chart = new ReportChart(title, points);
        return this;
    }

    ReportBuilder note(String note) {
        this.note = note;
        return this;
    }

    Table table(String title) {
        Table t = new Table(title);
        tables.add(null);
        t.index = tables.size() - 1;
        t.owner = this;
        return t;
    }

    ReportView build() {
        return new ReportView(key, name, from, to, kpis, chart, tables, note);
    }

    /** A value as plain text for its type; null → "". */
    static String text(Object v, String type) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal b) {
            return MONEY.equals(type) ? Money.of(b).toPlainString() : b.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Number n && MONEY.equals(type)) {
            return Money.of(BigDecimal.valueOf(n.doubleValue())).toPlainString();
        }
        return v.toString();
    }

    static ChartPoint point(String label, BigDecimal value, String title) {
        return new ChartPoint(label, Money.of(value).toPlainString(), title);
    }

    /** A table being built; {@link #done()} adds it to the report in the order it was started. */
    static final class Table {

        private final String title;
        private final List<ReportColumn> columns = new ArrayList<>();
        private final List<List<String>> rows = new ArrayList<>();
        private final List<Integer> bold = new ArrayList<>();
        private List<String> footer;
        private int index;
        private ReportBuilder owner;

        private Table(String title) {
            this.title = title;
        }

        Table col(String header, String type) {
            columns.add(new ReportColumn(header, type));
            return this;
        }

        Table row(Object... values) {
            rows.add(cells(values));
            return this;
        }

        Table boldRow(Object... values) {
            bold.add(rows.size());
            return row(values);
        }

        Table footer(Object... values) {
            footer = cells(values);
            return this;
        }

        int size() {
            return rows.size();
        }

        private List<String> cells(Object... values) {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                out.add(text(values[i], i < columns.size() ? columns.get(i).type() : TEXT));
            }
            return out;
        }

        ReportBuilder done() {
            owner.tables.set(index, new ReportTable(title, List.copyOf(columns), rows, footer, bold));
            return owner;
        }
    }
}
