package org.opendiplom.printing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;

/**
 * Blanks laid out by {@link BlankLayout} as one PDF, a page a side, printed
 * at 100% with the calibration of the printer added to every text.
 *
 * <p>A sample carries «ОБРАЗЕЦ» across each page: it shows what the blank
 * will hold before the document is complete, and cannot pass for one.
 */
public final class BlankPdf implements AutoCloseable {
    private static final float POINTS_PER_MM = 72f / 25.4f;
    private static final String SAMPLE = "ОБРАЗЕЦ";
    private static final float SAMPLE_SIZE = 120;
    private static final float SAMPLE_ALPHA = 0.12f;
    /** Outline that thickens the regular font when there is no bold one, points per point of size. */
    private static final float FAKE_BOLD = 0.03f;
    private static final double NOTES_WIDTH = 210;
    private static final double NOTES_HEIGHT = 297;
    private static final double NOTES_MARGIN = 20;
    private static final double NOTES_GAP = 2;
    private static final double NOTES_LINE = 5.5;
    private static final int NOTES_TITLE = 14;
    private static final int NOTES_SIZE = 11;

    private final PDDocument document = new PDDocument();
    private final PDFont regular;
    private final PDFont bold;
    private final boolean fakeBold;
    private final Calibration calibration;
    private final Map<String, Boolean> printable = new HashMap<>();

    /**
     * @param regular TrueType font with Cyrillic
     * @param bold its bold face, {@code null} to thicken the regular one
     */
    public BlankPdf(final Path regular, final Path bold, final Calibration calibration) throws IOException {
        this.regular = PDType0Font.load(this.document, regular.toFile());
        this.fakeBold = bold == null;
        this.bold = bold == null ? this.regular : PDType0Font.load(this.document, bold.toFile());
        this.calibration = calibration;
    }

    /** Widths in the fonts of this PDF, for {@link BlankLayout}. */
    public BlankLayout.Measure measure() {
        return (text, pt, isBold) -> {
            try {
                return this.font(isBold).getStringWidth(this.printable(text, this.font(isBold))) / 1000.0 * pt
                    / POINTS_PER_MM;
            } catch (final IOException error) {
                throw new UncheckedIOException(error);
            }
        };
    }

    /** Adds the pages of one document. */
    public void add(final List<BlankLayout.Page> pages, final boolean sample) throws IOException {
        for (final BlankLayout.Page laid : pages) {
            final PDRectangle size = new PDRectangle(points(laid.width), points(laid.height));
            final PDPage page = new PDPage(size);
            this.document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(this.document, page)) {
                for (final BlankLayout.Line line : laid.lines) {
                    final PDFont font = this.font(line.bold);
                    content.beginText();
                    content.setFont(font, line.pt);
                    if (line.bold && this.fakeBold) {
                        content.setRenderingMode(RenderingMode.FILL_STROKE);
                        content.setLineWidth(line.pt * FAKE_BOLD);
                    } else {
                        content.setRenderingMode(RenderingMode.FILL);
                    }
                    content.newLineAtOffset(
                        points((float) line.x + this.calibration.dx()),
                        size.getHeight() - points((float) line.baseline + this.calibration.dy())
                    );
                    content.showText(this.printable(line.text, font));
                    content.endText();
                }
                if (sample) {
                    this.sample(content, size);
                }
            }
        }
    }

    /** A page of A4 with notes, after the documents: what a sample could not print as it should. */
    public void notes(final String title, final List<String> notes) throws IOException {
        final BlankLayout.Page page = new BlankLayout.Page(NOTES_WIDTH, NOTES_HEIGHT);
        double y = NOTES_MARGIN;
        page.lines.add(new BlankLayout.Line(title, NOTES_MARGIN, y, NOTES_TITLE, true));
        final BlankLayout.Measure measure = this.measure();
        final double width = NOTES_WIDTH - 2 * NOTES_MARGIN;
        for (final String note : notes) {
            y += NOTES_GAP;
            String line = "";
            for (final String word : ("— " + note).split(" ")) {
                final String longer = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && measure.width(longer, NOTES_SIZE, false) > width) {
                    page.lines.add(new BlankLayout.Line(line, NOTES_MARGIN, y += NOTES_LINE, NOTES_SIZE, false));
                    line = word;
                } else {
                    line = longer;
                }
            }
            page.lines.add(new BlankLayout.Line(line, NOTES_MARGIN, y += NOTES_LINE, NOTES_SIZE, false));
        }
        this.add(List.of(page), false);
    }

    /** The finished PDF. */
    public byte[] pdf() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.document.save(out);
        return out.toByteArray();
    }

    @Override
    public void close() throws IOException {
        this.document.close();
    }

    private void sample(final PDPageContentStream content, final PDRectangle size) throws IOException {
        final PDExtendedGraphicsState faint = new PDExtendedGraphicsState();
        faint.setNonStrokingAlphaConstant(SAMPLE_ALPHA);
        content.saveGraphicsState();
        content.setGraphicsStateParameters(faint);
        content.setNonStrokingColor(0.5f, 0.5f, 0.5f);
        final float width = this.regular.getStringWidth(SAMPLE) / 1000 * SAMPLE_SIZE;
        final double angle = Math.atan2(size.getHeight(), size.getWidth());
        content.beginText();
        content.setFont(this.regular, SAMPLE_SIZE);
        content.setRenderingMode(RenderingMode.FILL);
        final Matrix matrix = Matrix.getRotateInstance(angle, size.getWidth() / 2, size.getHeight() / 2);
        matrix.translate(-width / 2, -SAMPLE_SIZE / 3);
        content.setTextMatrix(matrix);
        content.showText(SAMPLE);
        content.endText();
        content.restoreGraphicsState();
    }

    private PDFont font(final boolean isBold) {
        return isBold ? this.bold : this.regular;
    }

    /** The text without the characters the font has not, which PDFBox cannot write. */
    private String printable(final String text, final PDFont font) {
        final StringBuilder kept = new StringBuilder(text.length());
        text.codePoints().forEach(code -> {
            final String character = new String(Character.toChars(code));
            final boolean known = this.printable.computeIfAbsent(
                (font == this.bold ? "b" : "r") + character, key -> encodes(font, character)
            );
            kept.append(known ? character : "?");
        });
        return kept.toString();
    }

    private static boolean encodes(final PDFont font, final String character) {
        if (Character.isISOControl(character.codePointAt(0))) {
            return false;
        }
        try {
            font.encode(character);
            return true;
        } catch (final IOException | IllegalArgumentException error) {
            return false;
        }
    }

    private static float points(final double mm) {
        return (float) (mm * POINTS_PER_MM);
    }
}
