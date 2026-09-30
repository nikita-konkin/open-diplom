package org.opendiplom;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import org.opendiplom.printing.Calibration;
import org.opendiplom.printing.PdfSheet;

/**
 * Synthetic curricula in PDF, drawn as «Планы» saves them: a title, a grid
 * with numbered columns and names wrapped around their index. No real data.
 */
public final class PlanPdfs {
    public static final String DIRECTION =
        "НАПРАВЛЕНИЕ ПОДГОТОВКИ  09.03.02  ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ";
    public static final String WRAPPED_THREE =
        "Помехоустойчивость систем связи и электромагнитная совместимость радиоэлектронных средств";
    public static final String WRAPPED_TWO = "Элективная дисциплина 1 (Корпоративные информационные системы)";

    /** Column edges, mm: 1 — index, 2 — name, 3...15 — numbers. */
    private static final float[] EDGES =
        {10, 20, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220};
    /** Семестры экзаменов, зачетов, зачетов с оценкой, КП, КР: columns 3–7. */
    private static final int CONTROLS = 3;
    /** «в зачетных единицах» spans columns 8–10: всего, экзамены, учебные занятия. */
    private static final int CREDITS = 8;
    /** «в часах» spans columns 11–15: всего, экзамены, учебные занятия, контактная, самостоятельная. */
    private static final int TOTAL_HOURS = 11;
    private static final float TOP = 40;
    private static final float TITLES = 60;
    private static final float NUMBERS = 65;
    private static final float SIZE = 7;
    private static final float WIDTH = 297;
    private static final float HEIGHT = 210;

    /**
     * Columns 3–15 of «Математика»: exams in semesters 1 and 2, a course work
     * in 2; 16 credits of which 1 for exams; 576 hours, 200 of them contact.
     */
    public static final String[] MATHEMATICS =
        {"1, 2", "", "", "", "2", "16", "1", "15", "576", "36", "540", "200", "340"};

    private PlanPdfs() {
    }

    /**
     * The plan: a block total, «Математика» 16, a name wrapped to three lines
     * (5) and to two (5), «Этика» 1,5, an element without credits, the
     * facultative heading and «Теория игр» 3; blocks 1–3 of 70, 24 and 9, and
     * the program total of 103 credits and 3708 hours, 1500 of them contact.
     */
    public static byte[] plan(final Path font) throws IOException {
        try (PdfSheet sheet = new PdfSheet(WIDTH, HEIGHT, font, Calibration.NONE)) {
            sheet.text("УТВЕРЖДАЮ: ПРОРЕКТОР", 230, 10, SIZE)
                .text(DIRECTION, 60, 15, SIZE).text("Подпись", 250, 15, SIZE)
                .text("Профиль: (13) \"Интеллектуальные информационные системы и технологии\"", 60, 20, SIZE)
                .text("Форма обучения - Очная", 200, 20, SIZE)
                .text("Срок получения образования - 4 года", 200, 25, SIZE)
                .text("2022 г.п.", 60, 30, SIZE).text("Квалификация - Бакалавр", 200, 30, SIZE);
            rule(sheet, TOP);
            for (final float edge : new float[] {10, 90, 140, 170, 220}) {
                sheet.line(edge, TOP, edge, TITLES, 0.5f);
            }
            sheet.text("Структура ОП", 40, 52, SIZE).text("Семестры", 105, 52, 6)
                .text("Объем частей ОП", 143, 48, 6).text("в зачетных единицах", 143, 53, 6)
                .text("Объем частей ОП", 185, 48, 6).text("в часах", 190, 53, 6);
            rule(sheet, TITLES);
            columns(sheet, TITLES, NUMBERS, false);
            for (int column = 1; column < EDGES.length; ++column) {
                sheet.text(String.valueOf(column), (EDGES[column - 1] + EDGES[column]) / 2 - 1, NUMBERS - 1, SIZE);
            }
            rule(sheet, NUMBERS);
            float top = NUMBERS;
            top = row(sheet, top, 5, "", new String[] {"Блок 1. Дисциплины (модули)"}, volume("70", "2520"));
            top = row(sheet, top, 5, "Б.1.1.1", new String[] {"Математика"}, MATHEMATICS);
            top = row(sheet, top, 12, "Б.1.1.2",
                new String[] {"Помехоустойчивость систем связи и", "электромагнитная совместимость",
                    "радиоэлектронных средств"}, volume("5", "180"));
            top = row(sheet, top, 9, "Б.1.1.3",
                new String[] {"Элективная дисциплина 1 (Корпоративные", "информационные системы)"},
                volume("5", "180"));
            top = row(sheet, top, 5, "Б.1.1.4", new String[] {"Этика"}, volume("1,5", "54"));
            top = row(sheet, top, 5, "Б.1.1.5",
                new String[] {"Элективные дисциплины по физической культуре"}, volume("", "328"));
            top = row(sheet, top, 5, "", new String[] {"Факультативные дисциплины"}, volume("", ""));
            top = row(sheet, top, 5, "1", new String[] {"Теория игр"}, volume("3", "108"));
            top = row(sheet, top, 5, "", new String[] {"Блок 2. Практика"}, volume("24", "864"));
            top = row(sheet, top, 5, "", new String[] {"Блок 3. Государственная итоговая аттестация"},
                volume("9", "324"));
            total(sheet, top, new String[] {"3708", "324", "3384", "1500", "1884"});
            return sheet.pdf();
        }
    }

    /** A PDF of a single page with only the grid: what a scan gives, no text. */
    public static byte[] scan() throws IOException {
        try (PdfSheet sheet = new PdfSheet(WIDTH, HEIGHT, null, Calibration.NONE)) {
            rule(sheet, TOP);
            columns(sheet, TOP, NUMBERS, false);
            rule(sheet, NUMBERS);
            return sheet.pdf();
        }
    }

    /** A PDF with text and no plan table. */
    public static byte[] letter(final Path font) throws IOException {
        try (PdfSheet sheet = new PdfSheet(WIDTH, HEIGHT, font, Calibration.NONE)) {
            return sheet.text("Справка об обучении", 20, 20, 12).pdf();
        }
    }

    /**
     * A row: the name line by line, the index at the middle of the row, an
     * empty index for a cell merged over the index and the name.
     */
    private static float row(
        final PdfSheet sheet, final float top, final float height, final String index, final String[] name,
        final String[] values
    ) throws IOException {
        final float bottom = top + height;
        columns(sheet, top, bottom, index.isEmpty());
        final float step = (height - 2) / name.length;
        for (int line = 0; line < name.length; ++line) {
            sheet.text(name[line], EDGES[1] + 1, top + 1 + step * (line + 1) - 0.5f, SIZE);
        }
        final float middle = top + height / 2 + 1;
        if (!index.isEmpty()) {
            sheet.text(index, EDGES[0] + 1, middle, SIZE);
        }
        for (int column = 0; column < values.length; ++column) {
            if (!values[column].isEmpty()) {
                sheet.text(values[column], EDGES[CONTROLS - 1 + column] + 1, middle, SIZE);
            }
        }
        rule(sheet, bottom);
        return bottom;
    }

    /** Columns 3–15 with only the total credits and the total hours. */
    private static String[] volume(final String credits, final String hours) {
        final String[] values = new String[EDGES.length - CONTROLS];
        Arrays.fill(values, "");
        values[CREDITS - CONTROLS] = credits;
        values[TOTAL_HOURS - CONTROLS] = hours;
        return values;
    }

    /**
     * The program total as «Планы» draws it: the name cell spans two lines,
     * the upper one has titles over merged cells, the numbers are under them.
     */
    private static void total(final PdfSheet sheet, final float top, final String[] hours) throws IOException {
        final float middle = top + 5;
        final float bottom = top + 10;
        sheet.line(EDGES[0], top, EDGES[0], bottom, 0.5f).line(EDGES[2], top, EDGES[2], bottom, 0.5f);
        for (final float edge : new float[] {100, 110, 120, 130, 140, 170, 220}) {
            sheet.line(edge, top, edge, middle, 0.5f);
        }
        sheet.line(EDGES[2], middle, EDGES[EDGES.length - 1], middle, 0.5f);
        for (int edge = 3; edge < EDGES.length; ++edge) {
            sheet.line(EDGES[edge], middle, EDGES[edge], bottom, 0.5f);
        }
        sheet.text("ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", EDGES[0] + 1, top + 6, SIZE)
            .text("Зачетные единицы", EDGES[CREDITS - 1] + 1, top + 4, 5)
            .text("Часы", EDGES[TOTAL_HOURS - 1] + 15, top + 4, 5)
            .text("103", EDGES[CREDITS - 1] + 1, bottom - 1, SIZE);
        for (int column = 0; column < hours.length; ++column) {
            sheet.text(hours[column], EDGES[TOTAL_HOURS - 1 + column] + 1, bottom - 1, SIZE);
        }
        rule(sheet, bottom);
    }

    private static void columns(final PdfSheet sheet, final float top, final float bottom, final boolean merged)
        throws IOException {
        for (int edge = 0; edge < EDGES.length; ++edge) {
            if (!merged || edge != 1) {
                sheet.line(EDGES[edge], top, EDGES[edge], bottom, 0.5f);
            }
        }
    }

    private static void rule(final PdfSheet sheet, final float y) throws IOException {
        sheet.line(EDGES[0], y, EDGES[EDGES.length - 1], y, 0.5f);
    }
}
