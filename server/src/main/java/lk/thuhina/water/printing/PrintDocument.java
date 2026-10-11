package lk.thuhina.water.printing;

import java.util.List;

/**
 * A printed document in plain parts, so every print (bill now; receipts, invoices, statements later) has the same look:
 * the company header with the document {@code title} and {@code meta} lines on the right (the first in bold), the
 * {@code party} block (customer / supplier), a small {@code subtitle}, a table, a summary box, footer lines and
 * signature boxes. The renderer adds nothing of its own.
 */
public record PrintDocument(String title, List<String> meta, List<String> party, String subtitle, Table table,
                            List<SummaryRow> summary, List<String> footer, List<String> signatures) {

    /** Table: headers and rows of text; {@code numeric[i]} = column i is right-aligned. */
    public record Table(List<String> headers, boolean[] numeric, List<List<String>> rows) {
    }

    /** A label and value in the summary box; {@code bold} for the total. */
    public record SummaryRow(String label, String value, boolean bold) {
    }
}
