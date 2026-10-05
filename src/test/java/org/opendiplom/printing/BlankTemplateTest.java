package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendiplom.Templates;

/** A FastReport template read into millimetres. */
final class BlankTemplateTest {
    private static final double MM = 0.01;

    private static BlankTemplate.Band band(final BlankTemplate.Sheet sheet, final String name) {
        return sheet.bands.stream().filter(band -> band.name.equals(name)).findFirst().orElseThrow();
    }

    private static BlankTemplate.Memo memo(final List<BlankTemplate.Memo> memos, final String name) {
        return memos.stream().filter(memo -> memo.name.equals(name)).findFirst().orElseThrow();
    }

    @Test
    void cannotReadGzippedTemplate() throws Exception {
        final BlankTemplate template = BlankTemplate.of(Templates.gzip(Templates.supplement()));
        assertEquals(2, template.sheets.size(), "The template kept as CyberDiploma keeps it was not read");
    }

    @Test
    void cannotTakeOtherFileForTemplate() {
        final IOException error = assertThrows(IOException.class,
            () -> BlankTemplate.of("<html><body>не шаблон</body></html>".getBytes(StandardCharsets.UTF_8)),
            "A page that is not a template was taken for one");
        assertTrue(error.getMessage().contains("не шаблон FastReport"), "The refusal did not say why: " + error.getMessage());
    }

    @Test
    void cannotMisplaceFieldOfPage() throws Exception {
        final BlankTemplate.Sheet sheet = BlankTemplate.of(Templates.supplement()).sheets.get(0);
        final BlankTemplate.Memo number = memo(sheet.memos, "memNumber");
        assertEquals(800 / BlankTemplate.PX, number.x, MM, "Pixels at 96 dpi were not turned into millimetres");
        assertEquals(11, number.fontPt, "A font of 15 pixels is not 11 points, as Delphi rounds it");
        assertEquals(BlankTemplate.Align.CENTER, number.align, "The alignment was lost");
        assertEquals(1 / BlankTemplate.PX, number.gapY, MM, "The gap FastReport takes by default was not taken");
        final BlankTemplate.Memo last = memo(sheet.memos, "memLast");
        assertEquals(18, last.fontPt, "A font of 24 pixels is not 18 points");
        assertTrue(!last.wrap && last.gapX == 0, "A field that does not wrap, without gaps, was read otherwise");
    }

    @Test
    void cannotLoseLineSpacingOfScript() throws Exception {
        final BlankTemplate.Band table = band(BlankTemplate.of(Templates.supplement()).sheets.get(1), "bndTable");
        for (final String name : Arrays.asList("memName", "memHours", "memRating")) {
            assertEquals(1.5 / BlankTemplate.PX, memo(table.memos, name).lineSpacing, MM,
                "The line spacing the script gives to " + name + " was not taken");
        }
    }

    @Test
    void cannotStackBandsInOrderOfFile() throws Exception {
        final BlankTemplate.Sheet sheet = BlankTemplate.of(Templates.supplement()).sheets.get(1);
        assertEquals(Arrays.asList("MasterData1", "bndTable"),
            Arrays.asList(sheet.bands.get(0).name, sheet.bands.get(1).name),
            "The bands were not stacked by their position");
        final BlankTemplate.Band table = sheet.bands.get(1);
        assertTrue(
            "Модули и разделы".equals(table.dataset) && table.stretched && table.lastLine,
            "The table was not taken for rows of modules that stretch and grow by the script"
        );
        assertEquals(22.5 / BlankTemplate.PX, table.height, MM, "A height with a decimal comma was misread");
        assertTrue(sheet.bands.get(0).dataset.isEmpty() && sheet.bands.get(0).count == 1,
            "The spacer printed once was taken for rows of data");
        assertEquals(189 / BlankTemplate.PX, sheet.headerHeight, MM, "The page header was lost");
        assertEquals(47 / BlankTemplate.PX, sheet.footerHeight, MM, "The page footer was lost");
    }

    @Test
    void cannotIgnoreColumnPositions() throws Exception {
        final BlankTemplate template = BlankTemplate.of(Templates.supplement());
        assertEquals(Arrays.asList(0.0, 209.0), template.sheets.get(1).columnPositions,
            "The second column was not put where the template says, 1 mm left of its width");
    }

    @Test
    void cannotPrintSubreportOnItsOwn() throws Exception {
        final BlankTemplate template = BlankTemplate.of(Templates.supplement());
        assertTrue(
            template.sheets.stream().noneMatch(sheet -> "Page3".equals(sheet.name))
                && "Page3".equals(template.sheets.get(0).subreports.get(0).page.name),
            "The page of a subreport printed as a sheet of its own"
        );
        assertEquals(
            new LinkedHashSet<>(Arrays.asList("Модули и разделы", "Дополнительные сведения")), template.datasets(),
            "The data sets of the template, with the subreport, were not all found"
        );
    }
}
