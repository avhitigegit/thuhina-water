package lk.thuhina.water.reports.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.printing.CompanyHeaderService;
import lk.thuhina.water.printing.PdfRenderer;
import lk.thuhina.water.printing.PrintDocument;
import lk.thuhina.water.reports.dto.ReportDtos.ReportColumn;
import lk.thuhina.water.reports.dto.ReportDtos.ReportKpi;
import lk.thuhina.water.reports.dto.ReportDtos.ReportTable;
import lk.thuhina.water.reports.dto.ReportDtos.ReportView;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Report exports (design 10): PDF with the company header, report name, range, KPIs and the same tables; Excel with a
 * Summary sheet (range and KPIs) and one sheet per table, numbers and dates as real Excel values with formats.
 */
@Service
public class ReportExportService {

    /** Wide tables print on landscape pages. */
    private static final int PORTRAIT_COLUMNS = 6;

    private final CompanyHeaderService company;
    private final PdfRenderer renderer;

    public ReportExportService(CompanyHeaderService company, PdfRenderer renderer) {
        this.company = company;
        this.renderer = renderer;
    }

    /** "Sales-report-2026-09-01-to-2026-10-14.pdf" */
    public static String filename(ReportView r, String extension) {
        return r.name().replaceAll("[^A-Za-z0-9]+", "-") + "-report-" + r.from() + "-to-" + r.to() + "." + extension;
    }

    /** A value as shown on the screen: money "1,350.00", numbers "1,200", dates DD/MM/YYYY. */
    static String shown(String value, String type) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return switch (type) {
            case ReportBuilder.MONEY -> isNumber(value) ? Money.format(new BigDecimal(value)).replace("Rs. ", "") : value;
            case ReportBuilder.NUMBER -> isNumber(value) ? String.format("%,d", new BigDecimal(value).longValue()) : value;
            case ReportBuilder.DATE -> isDate(value) ? BusinessDates.format(LocalDate.parse(value)) : value;
            default -> value;
        };
    }

    /** Footers put words ("Total") in number and date columns – those stay text. */
    private static boolean isNumber(String v) {
        return v.matches("-?\\d+(\\.\\d+)?");
    }

    private static boolean isDate(String v) {
        return v.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    // ------------------------------------------------------------------ PDF

    public byte[] pdf(ReportView r) {
        return renderer.render(company.current(), document(r));
    }

    /** The PDF as plain parts (also used by tests). */
    public PrintDocument document(ReportView r) {
        boolean wide = r.tables().stream().anyMatch(t -> t.columns().size() > PORTRAIT_COLUMNS);
        List<PrintDocument.Table> tables = new ArrayList<>();
        if (!r.kpis().isEmpty()) {
            List<List<String>> rows = r.kpis().stream().map(k -> List.of(k.label(), kpiText(k))).toList();
            tables.add(new PrintDocument.Table(List.of("Summary", ""), new boolean[] {false, true}, rows, new float[] {3f, 2f}, 0, null));
        }
        for (ReportTable t : r.tables()) {
            boolean[] numeric = new boolean[t.columns().size()];
            for (int i = 0; i < numeric.length; i++) {
                String type = t.columns().get(i).type();
                numeric[i] = ReportBuilder.MONEY.equals(type) || ReportBuilder.NUMBER.equals(type);
            }
            List<List<String>> rows = new ArrayList<>();
            for (List<String> row : t.rows()) {
                rows.add(shownRow(row, t.columns()));
            }
            if (rows.isEmpty()) {
                List<String> empty = new ArrayList<>();
                empty.add("Nothing in this period.");
                while (empty.size() < t.columns().size()) {
                    empty.add("");
                }
                rows.add(empty);
            }
            if (t.footer() != null) {
                rows.add(shownRow(t.footer(), t.columns()));
            }
            List<String> headers = t.columns().stream().map(ReportColumn::header).toList();
            tables.add(new PrintDocument.Table(headers, numeric, rows, widths(t), 0, t.title()));
        }
        List<String> footer = r.note() == null ? List.of() : List.of(r.note());
        return new PrintDocument(r.name().toUpperCase() + " REPORT", List.of(BusinessDates.format(r.from()) + " – " + BusinessDates.format(r.to())),
                List.of(), null, tables, List.of(), footer, List.of(), wide ? PrintDocument.Page.A4_LANDSCAPE : PrintDocument.Page.A4_PORTRAIT);
    }

    private static List<String> shownRow(List<String> row, List<ReportColumn> columns) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            out.add(shown(row.get(i), i < columns.size() ? columns.get(i).type() : ReportBuilder.TEXT));
        }
        return out;
    }

    private static String kpiText(ReportKpi k) {
        return ReportBuilder.MONEY.equals(k.type()) && isNumber(k.value()) ? Money.format(new BigDecimal(k.value())) : shown(k.value(), k.type());
    }

    /** Text columns wider than numbers; a long last text column (names, reasons) gets more room. */
    private static float[] widths(ReportTable t) {
        float[] w = new float[t.columns().size()];
        for (int i = 0; i < w.length; i++) {
            String type = t.columns().get(i).type();
            w[i] = ReportBuilder.TEXT.equals(type) ? 3f : ReportBuilder.DATE.equals(type) ? 1.6f : 1.5f;
        }
        return w;
    }

    // ------------------------------------------------------------------ Excel

    public byte[] xlsx(ReportView r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles s = new Styles(wb);
            Sheet summary = wb.createSheet("Summary");
            int n = 0;
            Row title = summary.createRow(n++);
            cell(title, 0, r.name() + " report", s.title);
            Row range = summary.createRow(n++);
            cell(range, 0, BusinessDates.format(r.from()) + " – " + BusinessDates.format(r.to()), null);
            n++;
            for (ReportKpi k : r.kpis()) {
                Row row = summary.createRow(n++);
                cell(row, 0, k.label(), s.bold);
                value(row, 1, k.value(), k.type(), s);
            }
            if (r.note() != null) {
                n++;
                cell(summary.createRow(n), 0, r.note(), null);
            }
            summary.setColumnWidth(0, 40 * 256);
            summary.setColumnWidth(1, 20 * 256);

            Set<String> names = new HashSet<>(Set.of("Summary"));
            for (ReportTable t : r.tables()) {
                Sheet sheet = wb.createSheet(sheetName(t.title(), names));
                int rn = 0;
                cell(sheet.createRow(rn++), 0, t.title(), s.bold);
                Row head = sheet.createRow(rn++);
                for (int i = 0; i < t.columns().size(); i++) {
                    cell(head, i, t.columns().get(i).header(), s.header);
                }
                for (List<String> values : t.rows()) {
                    Row row = sheet.createRow(rn++);
                    for (int i = 0; i < values.size() && i < t.columns().size(); i++) {
                        value(row, i, values.get(i), t.columns().get(i).type(), s);
                    }
                }
                if (t.footer() != null) {
                    Row row = sheet.createRow(rn);
                    for (int i = 0; i < t.footer().size() && i < t.columns().size(); i++) {
                        value(row, i, t.footer().get(i), t.columns().get(i).type(), s);
                    }
                }
                for (int i = 0; i < t.columns().size(); i++) {
                    int width = t.columns().get(i).header().length();
                    for (List<String> values : t.rows()) {
                        if (i < values.size()) {
                            width = Math.max(width, shown(values.get(i), t.columns().get(i).type()).length());
                        }
                    }
                    sheet.setColumnWidth(i, Math.min(60, width + 2) * 256);
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sheet names: at most 31 characters, no []:*?/\ and unique. */
    static String sheetName(String title, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(title);
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String name = base;
        for (int i = 2; used.contains(name); i++) {
            name = base + " " + i;
        }
        used.add(name);
        return name;
    }

    private static void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    /** A typed value: money and numbers as numbers, dates as dates, the rest as text. */
    private static void value(Row row, int col, String v, String type, Styles s) {
        if (v == null || v.isEmpty()) {
            row.createCell(col);
            return;
        }
        Cell c = row.createCell(col);
        if ((ReportBuilder.MONEY.equals(type) || ReportBuilder.NUMBER.equals(type)) && isNumber(v)) {
            c.setCellValue(new BigDecimal(v).doubleValue());
            c.setCellStyle(ReportBuilder.MONEY.equals(type) ? s.money : s.number);
        } else if (ReportBuilder.DATE.equals(type) && isDate(v)) {
            c.setCellValue(LocalDate.parse(v));
            c.setCellStyle(s.date);
        } else {
            c.setCellValue(v);
        }
    }

    private static final class Styles {
        final CellStyle money;
        final CellStyle number;
        final CellStyle date;
        final CellStyle bold;
        final CellStyle header;
        final CellStyle title;

        Styles(XSSFWorkbook wb) {
            DataFormat f = wb.createDataFormat();
            money = wb.createCellStyle();
            money.setDataFormat(f.getFormat("#,##0.00"));
            number = wb.createCellStyle();
            number.setDataFormat(f.getFormat("#,##0"));
            date = wb.createCellStyle();
            date.setDataFormat(f.getFormat("dd/mm/yyyy"));
            Font b = wb.createFont();
            b.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(b);
            header = wb.createCellStyle();
            header.setFont(b);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);
        }
    }
}
