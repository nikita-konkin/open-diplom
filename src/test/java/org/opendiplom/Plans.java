package org.opendiplom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opendiplom.plans.PlanRow;

/**
 * Synthetic plan rows whose sums hold, as «Планы» keeps them: a part with
 * the physical culture electives outside its sums, facultatives under their
 * heading, a practice as its kind and type, and two rows with one index.
 */
public final class Plans {
    public static final String PRACTICE = "Преддипломная практика";

    private Plans() {
    }

    /** 21 credits: block 1 of 12, block 2 of 6, block 3 of 3. */
    public static List<PlanRow> bachelor() {
        return new ArrayList<>(Arrays.asList(
            row("", "Блок 1. Дисциплины (модули)", "", "12 1 11", "432 36 396 150 246"),
            row("Б.1.1", "Обязательная часть", "", "12 1 11", "432 36 396 150 246"),
            row("Б.1.1.1", "Математика", "1;;;;2", "8 1 7", "288 36 252 100 152"),
            row("Б.1.1.2", "Физика", ";2;;;", "4 - 4", "144 - 144 50 94"),
            row("Б.1.1.3", "Элективные дисциплины по физической культуре и спорту (Общая физическая подготовка / "
                + "Спортивные секции)", ";1,2;;;", "- - -", "- - 328 100 228"),
            row("", "Факультативные дисциплины", "", "- - -", "- - - - -"),
            row("1", "Теория игр", ";3;;;", "2 - 2", "72 - 72 36 36"),
            row("", "Блок 2. Практика", "", "6 - 6", "216 - 216 10 206"),
            row("Б.2.1", "Обязательная часть", "", "6 - 6", "216 - 216 10 206"),
            row("Б.2.1.1", "Производственная практика", ";;8;;", "6 - 6", "216 - 216 10 206"),
            row("Б.2.1.1.1", PRACTICE, ";;8;;", "6 - 6", "216 - 216 10 206"),
            row("", "Блок 3. Государственная итоговая аттестация", "", "3 - 3", "108 - 108 - 108"),
            row("Б.3.1", "Обязательная часть", "", "3 - 3", "108 - 108 - 108"),
            row("Б.3.1.1", "Выполнение и защита выпускной квалификационной работы", "", "2 - 2", "72 - 72 - 72"),
            row("Б.3.1.1", "Подготовка к сдаче и сдача государственного экзамена", "", "1 - 1", "36 - 36 - 36"),
            row("", "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", "", "21 1 20", "756 36 720 160 560")
        ));
    }

    /**
     * A workbook as «Планы» saves it: title lines, the column titles, the
     * row of column numbers and the rows; blocks and headings in the index
     * column, as in the real sheets.
     */
    public static byte[] workbook(final List<PlanRow> rows, final String... title) {
        final List<Object[]> lines = new ArrayList<>();
        for (final String line : title) {
            lines.add(new Object[] {"", line});
        }
        lines.add(new Object[] {"Индекс", "Структура ОП", "Кафедра", "РАСПРЕДЕЛЕНИЕ ПО СЕМЕСТРАМ", null, null, null,
            null, "Объем частей ОП\nв зачетных единицах", null, null, "Объем частей ОП\nв часах"});
        lines.add(new Object[] {"", "", "", "Экзамены", "Зачеты", "Зачеты с оценкой", "КП", "КР", "Всего",
            "Экзамены", "Учебные занятия", "Всего", "Экзамены", "Учебные занятия", "Контактная работа",
            "Самостоятельная работа"});
        lines.add(new Object[] {1, 2, 3, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});
        for (final PlanRow row : rows) {
            final Object[] cells = new Object[16];
            if (row.index().isEmpty()) {
                cells[0] = row.name();
            } else {
                cells[0] = row.index();
                cells[1] = row.name();
                cells[2] = "Кафедра";
            }
            for (int form = 0; form < PlanRow.CONTROLS; ++form) {
                cells[3 + form] = row.controls().get(form).isEmpty() ? null : row.controls().get(form);
            }
            for (int column = 0; column < PlanRow.CREDITS; ++column) {
                cells[8 + column] = row.creditColumns().get(column);
            }
            for (int column = 0; column < PlanRow.HOURS; ++column) {
                cells[11 + column] = row.hours().get(column);
            }
            lines.add(cells);
        }
        return Books.book(lines.toArray(new Object[0][]));
    }

    /**
     * A row from compact texts.
     *
     * @param controls semesters of the five forms of control split by «;»
     * @param credits credits columns split by spaces, «-» for an empty cell
     * @param hours hours columns the same way
     */
    public static PlanRow row(
        final String index, final String name, final String controls, final String credits, final String hours
    ) {
        return new PlanRow(index, name, Arrays.asList(controls.split(";", -1)), numbers(credits), numbers(hours));
    }

    /** The rows with the row of a name replaced. */
    public static List<PlanRow> with(final List<PlanRow> rows, final String name, final PlanRow replacement) {
        final List<PlanRow> changed = new ArrayList<>(rows);
        for (int position = 0; position < changed.size(); ++position) {
            if (changed.get(position).name().equals(name)) {
                changed.set(position, replacement);
                return changed;
            }
        }
        throw new IllegalArgumentException("No row «" + name + "»");
    }

    private static List<Double> numbers(final String text) {
        final List<Double> numbers = new ArrayList<>();
        for (final String number : text.isEmpty() ? new String[0] : text.split(" ")) {
            numbers.add("-".equals(number) ? null : Double.valueOf(number));
        }
        return numbers;
    }
}
