package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

final class PdfSheetTest {
    private static final float MM_PER_POINT = 25.4f / 72f;

    /** Top-left of the first letter's baseline, millimetres from the sheet's top-left corner. */
    private static float[] origin(final byte[] pdf) throws IOException {
        final List<TextPosition> letters = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            final PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(final String text, final List<TextPosition> positions) {
                    letters.addAll(positions);
                }
            };
            stripper.getText(document);
        }
        final TextPosition first = letters.get(0);
        return new float[] {first.getXDirAdj() * MM_PER_POINT, first.getYDirAdj() * MM_PER_POINT};
    }

    private static byte[] field(final Calibration calibration) throws IOException {
        try (PdfSheet sheet = new PdfSheet(297, 210, null, calibration)) {
            return sheet.text("Field", 40, 60, 12).pdf();
        }
    }

    @Test
    void cannotPlaceFieldMoreThanHalfMillimetreAway() throws Exception {
        final float[] origin = origin(field(Calibration.NONE));
        assertTrue(
            Math.abs(origin[0] - 40) <= 0.5 && Math.abs(origin[1] - 60) <= 0.5,
            "The field is off its place by more than 0.5 mm: " + origin[0] + ", " + origin[1]
        );
    }

    @Test
    void cannotIgnorePrinterCalibration() throws Exception {
        final float[] origin = origin(field(new Calibration(1.5f, -2)));
        assertTrue(
            Math.abs(origin[0] - 41.5) <= 0.1 && Math.abs(origin[1] - 58) <= 0.1,
            "The calibration did not move the field: " + origin[0] + ", " + origin[1]
        );
    }

    @Test
    void cannotAcceptCalibrationOfCentimetres() {
        assertThrows(
            IllegalArgumentException.class, () -> new Calibration(15, 0),
            "A 15 mm shift was taken for a calibration, not for a wrong paper size"
        );
    }

    @Test
    void cannotPrintTestSheetWithoutCyrillic() throws Exception {
        final Optional<Path> font = Fonts.serif(null);
        assumeTrue(font.isPresent(), "No Cyrillic serif font on this machine");
        final String text;
        try (PDDocument document = Loader.loadPDF(TestSheet.pdf(font.get(), Calibration.NONE))) {
            text = new PDFTextStripper().getText(document);
        }
        assertTrue(text.contains(TestSheet.FIELD), "The test sheet lost its Cyrillic text");
    }

    @Test
    void cannotLoseTestFieldPosition() throws Exception {
        final Optional<Path> font = Fonts.serif(null);
        assumeTrue(font.isPresent(), "No Cyrillic serif font on this machine");
        final byte[] pdf;
        try (PdfSheet sheet = new PdfSheet(TestSheet.WIDTH, TestSheet.HEIGHT, font.get(), Calibration.NONE)) {
            pdf = sheet.text(TestSheet.FIELD, TestSheet.FIELD_X, TestSheet.FIELD_Y, 12).pdf();
        }
        final float[] origin = origin(pdf);
        assertEquals(
            TestSheet.FIELD_X, origin[0], 0.5,
            "A Cyrillic field is off its place by more than 0.5 mm"
        );
    }
}
