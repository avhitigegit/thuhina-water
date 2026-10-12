package lk.thuhina.water.printing;

import java.util.List;

/**
 * A printed document in plain parts, so every print (bill, delivery and load sheets; receipts, invoices, statements later)
 * has the same look: the company header with the document {@code title} and {@code meta} lines on the right (the first in
 * bold), the {@code party} block (customer / supplier), a small {@code subtitle}, one or more tables, a summary box, footer
 * lines and signature boxes, on the given {@code page}. {@code watermark} (e.g. DRAFT, CANCELLED; null = none) is printed
 * large and light across every page. The renderer adds nothing of its own.
 */
public record PrintDocument(String title, List<String> meta, List<String> party, String subtitle, List<Table> tables,
                            List<SummaryRow> summary, List<String> footer, List<String> signatures, Page page, String watermark) {

    /** Without a watermark. */
    public PrintDocument(String title, List<String> meta, List<String> party, String subtitle, List<Table> tables,
                         List<SummaryRow> summary, List<String> footer, List<String> signatures, Page page) {
        this(title, meta, party, subtitle, tables, summary, footer, signatures, page, null);
    }

    /** One table on an A5 page (bills). */
    public PrintDocument(String title, List<String> meta, List<String> party, String subtitle, Table table, List<SummaryRow> summary,
                         List<String> footer, List<String> signatures) {
        this(title, meta, party, subtitle, table == null ? List.of() : List.of(table), summary, footer, signatures, Page.A5_PORTRAIT,
                null);
    }

    /** Page size: A5 portrait for bills; A4 portrait for purchase orders; A4 landscape for the wide delivery and load sheets. */
    public enum Page {
        A5_PORTRAIT, A4_PORTRAIT, A4_LANDSCAPE
    }

    /**
     * Table: headers and rows of text; {@code numeric[i]} = column i is right-aligned. A row with a single cell is a group
     * heading across all columns (e.g. the area). {@code widths} = relative column widths (null = the bill's widths);
     * {@code rowHeight} > 0 leaves room to write by hand in blank columns.
     */
    public record Table(List<String> headers, boolean[] numeric, List<List<String>> rows, float[] widths, float rowHeight) {

        public Table(List<String> headers, boolean[] numeric, List<List<String>> rows) {
            this(headers, numeric, rows, null, 0);
        }
    }

    /** A label and value in the summary box; {@code bold} for the total. */
    public record SummaryRow(String label, String value, boolean bold) {
    }
}
