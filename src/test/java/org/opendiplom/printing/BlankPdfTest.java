package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.opendiplom.Templates;
import org.opendiplom.catalog.Organization;

/** Blanks written to PDF with a Cyrillic serif font of the system. */
final class BlankPdfTest {
    private static final double MM_PER_POINT = 25.4 / 72;

    private static Path font() {
        final Optional<Path> font = Fonts.serif("");
        assumeTrue(font.isPresent(), "No Cyrillic serif font on this machine");
        return font.get();
    }

    /** The first letter of each text: x, baseline in millimetres, keyed by the text. */
    private static List<String> texts(final byte[] pdf) throws Exception {
        final List<String> texts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            final PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(final String text, final List<TextPosition> positions) {
                    final TextPosition first = positions.get(0);
                    texts.add(String.format(java.util.Locale.ROOT, "%s@%.1f,%.1f", text,
                        first.getXDirAdj() * MM_PER_POINT, first.getYDirAdj() * MM_PER_POINT));
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }
        return texts;
    }

    private static byte[] page(final Calibration calibration, final boolean sample, final String text) throws Exception {
        final BlankLayout.Page page = new BlankLayout.Page(297, 210);
        page.lines.add(new BlankLayout.Line(text, 40, 60, 12, false));
        try (BlankPdf pdf = new BlankPdf(font(), null, calibration)) {
            pdf.add(List.of(page), sample);
            return pdf.pdf();
        }
    }

    @Test
    void cannotPutTextOffItsPlace() throws Exception {
        assertEquals(List.of("Поле@40.0,60.0"), texts(page(Calibration.NONE, false, "Поле")),
            "The text is not where the layout put it");
    }

    @Test
    void cannotIgnoreCalibration() throws Exception {
        assertEquals(List.of("Поле@41.5,58.0"), texts(page(new Calibration(1.5f, -2), false, "Поле")),
            "The calibration of the printer did not move the text");
    }

    /** The letters in the order they are written, without spaces: a slanted text comes out letter by letter. */
    private static String letters(final byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s", "");
        }
    }

    @Test
    void cannotPrintSampleWithoutMark() throws Exception {
        assertTrue(letters(page(Calibration.NONE, true, "Поле")).contains("ОБРАЗЕЦ"),
            "A sample can pass for a document: it has no mark");
        assertFalse(letters(page(Calibration.NONE, false, "Поле")).contains("ОБРАЗЕЦ"),
            "A document for the blank is marked as a sample");
    }

    @Test
    void cannotFailOnLetterFontHasNot() throws Exception {
        assertEquals(List.of("Поле?@40.0,60.0"), texts(page(Calibration.NONE, false, "Поле\u0007")),
            "A letter the font has not broke the PDF or went unmarked");
    }

    @Test
    void cannotPrintSupplementWithoutItsPages() throws Exception {
        final Set<String> problems = new LinkedHashSet<>();
        final BlankTemplate template = BlankTemplate.of(Templates.supplement());
        final byte[] pdf = BlankPrint.pdf(
            template, List.of(BlankData.sample("03", Organization.empty(), false)), font(), null, Calibration.NONE,
            true, problems
        );
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertEquals(2, document.getNumberOfPages(), "The supplement is not two sides: " + problems);
        }
        final String text = String.join("\n", texts(pdf));
        assertTrue(text.contains("Образцова") && text.contains("10001") && text.contains("Государственный экзамен"),
            "The sample lacks the graduate, the number or the attestation: " + text);
    }
}
