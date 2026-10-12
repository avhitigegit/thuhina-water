package lk.thuhina.water.printing;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * {@link PdfRenderer} with OpenPDF: Helvetica, the layout of the prototype's printed documents – A5 portrait for bills, A4
 * landscape for the delivery and load sheets.
 */
@Component
public class OpenPdfRenderer implements PdfRenderer {

    private static final Color GREY = new Color(110, 110, 110);
    private static final Color LINE = new Color(200, 200, 200);
    private static final Color GROUP = new Color(238, 244, 247);
    private static final Font NAME = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, GREY);

    @Override
    public byte[] render(CompanyHeader company, PrintDocument d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = d.page() == PrintDocument.Page.A4_LANDSCAPE ? new Document(PageSize.A4.rotate(), 28, 28, 28, 28)
                : new Document(PageSize.A5, 30, 30, 30, 30);
        PdfWriter.getInstance(doc, out);
        doc.addTitle(d.title() + (d.meta().isEmpty() ? "" : " " + d.meta().getFirst()));
        doc.addCreator("Thuhina Water");
        doc.open();
        doc.add(header(company, d));
        if (!d.party().isEmpty()) {
            Paragraph p = new Paragraph();
            p.setSpacingBefore(10);
            for (int i = 0; i < d.party().size(); i++) {
                p.add(new Phrase((i > 0 ? "\n" : "") + d.party().get(i), i == 0 ? BOLD : NORMAL));
            }
            doc.add(p);
        }
        if (d.subtitle() != null && !d.subtitle().isBlank()) {
            Paragraph p = new Paragraph(d.subtitle(), SMALL);
            p.setSpacingBefore(4);
            doc.add(p);
        }
        for (PrintDocument.Table table : d.tables()) {
            doc.add(table(table));
        }
        if (!d.summary().isEmpty()) {
            doc.add(summary(d.summary()));
        }
        for (String line : d.footer()) {
            Paragraph p = new Paragraph(line, NORMAL);
            p.setSpacingBefore(6);
            doc.add(p);
        }
        if (!d.signatures().isEmpty()) {
            doc.add(signatures(d.signatures()));
        }
        doc.close();
        return out.toByteArray();
    }

    private PdfPTable header(CompanyHeader company, PrintDocument d) {
        PdfPTable t = new PdfPTable(new float[] {3, 2});
        t.setWidthPercentage(100);
        PdfPCell left = cell(null, Element.ALIGN_LEFT);
        if (company.logo() != null) {
            try {
                Image logo = Image.getInstance(company.logo());
                logo.scaleToFit(90, 40);
                left.addElement(logo);
            } catch (Exception ignored) {
                // A logo that cannot be read is left out; the name is still printed.
            }
        }
        left.addElement(new Paragraph(company.name(), NAME));
        for (String line : company.lines()) {
            left.addElement(new Paragraph(line, SMALL));
        }
        t.addCell(left);
        PdfPCell right = cell(null, Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph(d.title(), TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        for (int i = 0; i < d.meta().size(); i++) {
            Paragraph p = new Paragraph(d.meta().get(i), i == 0 ? BOLD : NORMAL);
            p.setAlignment(Element.ALIGN_RIGHT);
            right.addElement(p);
        }
        t.addCell(right);
        return t;
    }

    private PdfPTable table(PrintDocument.Table table) {
        PdfPTable t = new PdfPTable(table.headers().size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(10);
        float[] widths = table.widths();
        if (widths == null) {
            widths = new float[table.headers().size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = i == 0 ? 4.4f : i == widths.length - 1 ? 2.2f : 1.7f;
            }
        }
        t.setWidths(widths);
        for (int i = 0; i < table.headers().size(); i++) {
            PdfPCell c = cell(new Phrase(table.headers().get(i), BOLD), table.numeric()[i] ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(GREY);
            t.addCell(c);
        }
        t.setHeaderRows(1);
        for (List<String> row : table.rows()) {
            if (row.size() == 1 && table.headers().size() > 1) {
                PdfPCell c = cell(new Phrase(row.getFirst(), BOLD), Element.ALIGN_LEFT);
                c.setColspan(table.headers().size());
                c.setBackgroundColor(GROUP);
                t.addCell(c);
                continue;
            }
            for (int i = 0; i < row.size(); i++) {
                PdfPCell c = cell(new Phrase(row.get(i), NORMAL), table.numeric()[i] ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                c.setBorder(table.rowHeight() > 0 ? Rectangle.BOX : Rectangle.BOTTOM);
                c.setBorderColor(LINE);
                if (table.rowHeight() > 0) {
                    c.setMinimumHeight(table.rowHeight());
                }
                t.addCell(c);
            }
        }
        return t;
    }

    private PdfPTable summary(List<PrintDocument.SummaryRow> rows) {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(55);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        for (PrintDocument.SummaryRow r : rows) {
            Font f = r.bold() ? BOLD : NORMAL;
            t.addCell(cell(new Phrase(r.label(), f), Element.ALIGN_LEFT));
            t.addCell(cell(new Phrase(r.value(), f), Element.ALIGN_RIGHT));
        }
        return t;
    }

    private PdfPTable signatures(List<String> boxes) {
        PdfPTable t = new PdfPTable(boxes.size());
        t.setWidthPercentage(100);
        t.setSpacingBefore(36);
        for (String s : boxes) {
            PdfPCell c = cell(new Phrase(s, SMALL), Element.ALIGN_LEFT);
            c.setBorder(Rectangle.TOP);
            c.setBorderColor(GREY);
            c.setPaddingRight(20);
            t.addCell(c);
        }
        return t;
    }

    private static PdfPCell cell(Phrase content, int align) {
        PdfPCell c = content == null ? new PdfPCell() : new PdfPCell(content);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        c.setPadding(3);
        return c;
    }
}
