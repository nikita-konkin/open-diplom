package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.opendiplom.Templates;

/**
 * The layout of a template, with letters 2 mm wide: the table of the
 * supplement takes 54 of them a line, its field being 414 whole pixels.
 */
final class BlankLayoutTest {
    private static final double MM = 0.01;
    private static final BlankLayout.Measure MONOSPACE = (text, pt, bold) -> text.length() * 2.0;
    /** 0.891 em of a 15-pixel font less the gap of 1 pixel above, millimetres. */
    private static final double BASELINE = (0.891 * 15 - 1) / BlankTemplate.PX;

    private final Set<String> problems = new LinkedHashSet<>();

    private List<BlankLayout.Page> layout(final byte[] template, final List<String[]> modules, final String... extra)
        throws Exception {
        final Map<String, Object> fields = new HashMap<>();
        fields.put("Студент.\"Фамилия\"", "Андреев");
        fields.put("Документ.\"Регистрационный номер\"", "10001");
        final Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        final List<Map<String, Object>> table = new ArrayList<>();
        for (final String[] module : modules) {
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("Модули и разделы.\"Наименование\"", module[0]);
            row.put("Модули и разделы.\"Трудоёмкость\"", module[1]);
            row.put("Модули и разделы.\"Оценка\"", module[2]);
            table.add(row);
        }
        rows.put("Модули и разделы", table);
        final List<Map<String, Object>> info = new ArrayList<>();
        for (final String line : extra) {
            info.add(Collections.singletonMap("Дополнительные сведения.\"Наименование\"", line));
        }
        rows.put("Дополнительные сведения", info);
        return BlankLayout.of(BlankTemplate.of(template), BlankData.of(fields, rows), MONOSPACE, this.problems);
    }

    private static BlankLayout.Line line(final BlankLayout.Page page, final String text) {
        return page.lines.stream().filter(line -> line.text.equals(text)).findFirst()
            .orElseThrow(() -> new AssertionError("No line «" + text + "» among " + page.lines));
    }

    /** Words of five letters, cut at a number of letters. */
    private static String words(final int letters, final String word) {
        final StringBuilder text = new StringBuilder();
        while (text.length() < letters) {
            text.append(text.length() == 0 ? "" : " ").append(word);
        }
        return text.substring(0, letters);
    }

    /** Pixels from the top of the sheet to millimetres. */
    private static double px(final double pixels) {
        return pixels / BlankTemplate.PX;
    }

    @Test
    void cannotGiveRowsOtherHeightThanCyberDiploma() throws Exception {
        final String two = words(60, "слово");
        final String three = words(120, "текст");
        final List<BlankLayout.Page> pages = this.layout(Templates.supplement(), List.of(
            new String[] {"Первый", "8 з.е.", "отлично"}, new String[] {two, "4 з.е.", "хорошо"},
            new String[] {three, "х", "зачтено"}, new String[] {"Последний", "2 з.е.", "отлично"}
        ));
        final BlankLayout.Page table = pages.get(1);
        // the header of 189 pixels and the spacer of 38; rows of 16, 32 and 49 pixels
        assertEquals(px(189 + 38) + BASELINE, line(table, "Первый").baseline, MM, "The first row is off its place");
        assertEquals(px(189 + 38 + 16) + BASELINE, line(table, two.substring(0, 53)).baseline, MM,
            "A row of one line is not 16 pixels high");
        assertEquals(px(189 + 38 + 16 + 32) + BASELINE, line(table, three.substring(0, 53)).baseline, MM,
            "A row of two lines is not 32 pixels high");
        assertEquals(px(189 + 38 + 16 + 32 + 49) + BASELINE, line(table, "Последний").baseline, MM,
            "A row of three lines is not 49 pixels high, the height rounded to whole pixels");
        assertEquals(px(189 + 38 + 16 + 16) + BASELINE, line(table, "хорошо").baseline, MM,
            "The grade of a row of two lines is not on its last line, as the script puts it");
        assertEquals(px(189 + 38 + 16) + BASELINE + px(16.33), line(table, two.substring(54)).baseline, MM,
            "The lines of a field are not 16.33 pixels apart");
    }

    @Test
    void cannotBreakLineWiderThanFieldInWholePixels() throws Exception {
        final String text = "x".repeat(26) + " " + "y".repeat(27);
        final List<BlankLayout.Page> pages = BlankLayout.of(
            BlankTemplate.of(Templates.supplement()),
            BlankData.of(new HashMap<>(), Map.<String, List<Map<String, Object>>>of("Модули и разделы", List.of(Map.<String, Object>of(
                "Модули и разделы.\"Наименование\"", text, "Модули и разделы.\"Трудоёмкость\"", "",
                "Модули и разделы.\"Оценка\"", ""
            )))), (line, pt, bold) -> line.length() * 2.03, this.problems
        );
        assertTrue(
            pages.get(1).lines.stream().anyMatch(line -> line.text.equals("x".repeat(26)))
                && pages.get(1).lines.stream().anyMatch(line -> line.text.equals("y".repeat(27))),
            "A line of 109.62 mm stayed whole in a field of 414.6 pixels, 109.70 mm, that FastReport takes as 414"
        );
    }

    @Test
    void cannotCentreTextOffItsField() throws Exception {
        final BlankLayout.Page page = this.layout(Templates.supplement(), List.of()).get(0);
        assertEquals(px(800 + 230 / 2.0) - 5 * 2.0 / 2, line(page, "10001").x, MM, "The number is not centred in its field");
        assertEquals(px(1150), line(page, "Андреев").x, MM, "A field without gaps did not start at its edge");
    }

    @Test
    void cannotOverflowColumnOrSheet() throws Exception {
        final List<String[]> rows = new ArrayList<>();
        for (int row = 0; row < 120; ++row) {
            rows.add(new String[] {"Строка " + row, "1 з.е.", "отлично"});
        }
        final List<BlankLayout.Page> pages = this.layout(Templates.supplement(), rows);
        // from 227 to 1075.5 pixels: 53 rows; from 189: 55 more
        assertEquals(px(189) + BASELINE, line(pages.get(1), "Строка 53").baseline, MM,
            "The second column did not start below the header");
        assertEquals(209 + px(57 + 2), line(pages.get(1), "Строка 53").x, MM,
            "The second column did not start where the template puts it");
        assertEquals(3, pages.size(), "The rows beyond the sheet did not go to a page of their own");
        assertEquals(px(189) + BASELINE, line(pages.get(2), "Строка 108").baseline, MM,
            "The page added did not start below the header, without the spacer printed once");
        assertTrue(this.problems.stream().anyMatch(problem -> problem.contains("добавлена страница")),
            "A page added to the blank went without a word: " + this.problems);
    }

    @Test
    void cannotLoseSubreportOrFooter() throws Exception {
        final String profile = words(100, "слово");
        final List<BlankLayout.Page> pages = this.layout(Templates.supplement(), List.of(), profile, "Форма обучения: очная");
        // a field of 686 whole pixels, 181.5 mm: fifteen words, 89 letters
        assertEquals(px(125) + BASELINE, line(pages.get(0), profile.substring(0, 89)).baseline, MM,
            "The additional information did not start at its subreport");
        assertEquals(px(125 + 32) + BASELINE, line(pages.get(0), "Форма обучения: очная").baseline, MM,
            "The second row of the subreport is off its place");
        assertEquals(px(1122.52 - 47 + 8) + BASELINE + px(2), line(pages.get(1), "2").baseline, 0.05,
            "The page number in the footer is off its place");
    }

    @Test
    void cannotPlaceDiplomaFields() throws Exception {
        final Map<String, Object> fields = new HashMap<>();
        fields.put("Документ.\"Регистрационный номер\"", "10001");
        fields.put("Документ.\"Диплом: дубликат\"", 1L);
        fields.put("Образовательное учреждение.\"Наименование\"", "университет");
        fields.put("Образовательное учреждение.\"Местонахождение\"", "г. Йошкар-Ола");
        fields.put("Студент.\"Председатель: Фамилия\"", "Иванов");
        fields.put("Студент.\"Председатель: Имя\"", "Иван");
        fields.put("Студент.\"Председатель: Отчество\"", "Петрович");
        fields.put("Студент.\"Дата решения госкомиссии\"", "2026-06-04");
        final BlankLayout.Page page = BlankLayout.of(
            BlankTemplate.of(Templates.diploma()), BlankData.of(fields, new HashMap<>()), MONOSPACE, this.problems
        ).get(0);
        assertEquals(7 + px(26 + 495 / 2.0) - 5, line(page, "10001").x, MM, "The left margin of the page was lost");
        assertTrue(line(page, "ДУБЛИКАТ").bold, "«ДУБЛИКАТ» lost its bold");
        assertEquals(7 + px(900 + 155 - 2) - 11 * 2, line(page, "Иванов И.П.").x, MM, "The chairman is not aligned right");
        assertEquals("04", line(page, "04").text, "The day of the decision lost its leading zero");
        // two lines centred in a field of 113 pixels
        final double middle = 0.2 + px(220 + 113 / 2.0);
        final BlankLayout.Line first = line(page, "университет");
        final BlankLayout.Line second = line(page, "г. Йошкар-Ола");
        assertTrue(first.baseline < middle && second.baseline > middle,
            "The name of the organization was not centred down its field: " + first.baseline + ", " + second.baseline);
        assertTrue(this.problems.isEmpty(), "The diploma had problems: " + this.problems);
    }
}
