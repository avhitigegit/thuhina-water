package lk.thuhina.water.printing;

/**
 * Turns a {@link PrintDocument} with the company header into PDF bytes (design 3, "printing"). An interface because a
 * second renderer (e.g. HTML for e-mail) is planned.
 */
public interface PdfRenderer {

    byte[] render(CompanyHeader company, PrintDocument document);
}
