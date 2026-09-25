package org.opendiplom.printing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * One printed sheet laid out in millimetres from its top-left corner.
 *
 * <p>Blanks are pre-printed: every field must land where the blank expects
 * it, so positions are in millimetres, the PDF is printed at 100%, and the
 * printer's own shift is corrected by a calibration offset.
 */
public final class PdfSheet implements AutoCloseable {
    private static final float POINTS_PER_MM = 72f / 25.4f;

    private final PDDocument document = new PDDocument();
    private final PDRectangle size;
    private final PDPageContentStream content;
    private final PDFont font;
    private final float dx;
    private final float dy;

    /**
     * @param widthMm sheet width: 297 for A4 landscape, 420 for A3 landscape
     * @param heightMm sheet height
     * @param font TrueType font with Cyrillic, or {@code null} for Times (Latin only)
     * @param calibration shift of this printer, millimetres right and down
     */
    public PdfSheet(
        final float widthMm, final float heightMm, final Path font, final Calibration calibration
    ) throws IOException {
        this.size = new PDRectangle(points(widthMm), points(heightMm));
        final PDPage page = new PDPage(this.size);
        this.document.addPage(page);
        this.font = font == null
            ? new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
            : PDType0Font.load(this.document, font.toFile());
        this.content = new PDPageContentStream(this.document, page);
        this.dx = calibration.dx();
        this.dy = calibration.dy();
    }

    /** Text with its baseline starting at a point, millimetres from the top-left corner. */
    public PdfSheet text(final String text, final float xMm, final float yMm, final float sizePt)
        throws IOException {
        this.content.beginText();
        this.content.setFont(this.font, sizePt);
        this.content.newLineAtOffset(this.x(xMm), this.y(yMm));
        this.content.showText(text);
        this.content.endText();
        return this;
    }

    /** A straight line between two points, millimetres from the top-left corner. */
    public PdfSheet line(final float x1, final float y1, final float x2, final float y2, final float widthPt)
        throws IOException {
        this.content.setLineWidth(widthPt);
        this.content.moveTo(this.x(x1), this.y(y1));
        this.content.lineTo(this.x(x2), this.y(y2));
        this.content.stroke();
        return this;
    }

    /** Width of a text in this font, millimetres. */
    public float widthMm(final String text, final float sizePt) throws IOException {
        return this.font.getStringWidth(text) / 1000f * sizePt / POINTS_PER_MM;
    }

    /** The finished PDF; the sheet cannot be drawn on afterwards. */
    public byte[] pdf() throws IOException {
        this.content.close();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        this.document.close();
    }

    static float points(final float mm) {
        return mm * POINTS_PER_MM;
    }

    private float x(final float mm) {
        return points(mm + this.dx);
    }

    private float y(final float mm) {
        return this.size.getHeight() - points(mm + this.dy);
    }
}
