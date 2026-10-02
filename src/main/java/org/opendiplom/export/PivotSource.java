package org.opendiplom.export;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;

/**
 * Graduates from the files of the current service: the pivot (a row per
 * «Название_тип_з.е.», a column per student «Фамилия И. О.») and the student
 * information file. Kept for the transition period, so the hand-corrected
 * pivots of past years can be exported again.
 *
 * <p>Every problem is collected and reported together; no student is dropped
 * silently or given another student's grades.
 */
public final class PivotSource {
    static final String INDEX = "Дисциплины";
    static final String THESIS_ROW = "выполнение и защита выпускной квалификационной работы";
    static final String STATE_EXAM_ROW = "подготовка к сдаче и сдача государственного экзамена";
    static final String STATE_EXAM = "Государственный экзамен";
    private static final Pattern WHOLE = Pattern.compile("[+-]?\\d+");

    private PivotSource() {
    }

    /**
     * @param pivot first sheet of the pivot workbook
     * @param info first sheet of the student information workbook
     * @throws WorkbookException the pivot has no «Дисциплины» column
     * @throws ValidationProblems the data has problems, all of them listed
     */
    public static List<Graduate> read(final Sheet pivot, final Sheet info, final LocalDate today)
        throws WorkbookException, ValidationProblems {
        final Table grades = new Table(pivot);
        final int index = grades.column(INDEX);
        if (index < 0) {
            throw new WorkbookException(
                "Сводная таблица: на первом листе нет колонки «Дисциплины». "
                    + "Загрузите сводную, построенную на вкладке «Сводная таблица»."
            );
        }
        grades.rows.removeIf(row -> row.stream().allMatch(Cells::missing));
        final StudentInfo students = StudentInfo.read(info, today);
        final List<String> problems = new ArrayList<>(students.problems());
        final List<Graduate> read = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final List<List<String>> people = new ArrayList<>();
        for (final StudentInfo.Entry entry : students.entries()) {
            read.add(new Graduate(
                entry.lastName, entry.firstName, entry.middleName, entry.birthDate, entry.previousDocument,
                entry.previousYear, entry.gekDate, entry.gekProtocol, entry.thesisTopic, entry.thesisGrade
            ));
            labels.add(entry.label());
            people.add(entry.key());
        }
        final List<Object> columns = new ArrayList<>(grades.columns);
        columns.remove(index);
        final Map<Integer, Integer> mapping = match(labels, people, columns, problems);
        final List<Graduate> graduates = new ArrayList<>();
        final LinkedHashSet<String> rowProblems = new LinkedHashSet<>();
        for (int person = 0; person < read.size(); ++person) {
            if (!mapping.containsKey(person)) {
                continue;
            }
            final Graduate graduate = read.get(person);
            final int column = grades.columns.indexOf(columns.get(mapping.get(person)));
            fill(graduate, labels.get(person), grades, index, column, problems, rowProblems);
            graduates.add(graduate);
        }
        problems.addAll(rowProblems);
        if (!problems.isEmpty()) {
            throw new ValidationProblems(problems);
        }
        return graduates;
    }

    /**
     * Maps each student to exactly one pivot column. A student without a column,
     * a student with several candidate columns and a column claimed by several
     * students are all problems.
     */
    private static Map<Integer, Integer> match(
        final List<String> labels, final List<List<String>> people,
        final List<Object> columns, final List<String> problems
    ) {
        final List<List<String>> keys = new ArrayList<>();
        for (final Object column : columns) {
            keys.add(Names.column(column));
        }
        final Map<Integer, Integer> mapping = new LinkedHashMap<>();
        for (int person = 0; person < people.size(); ++person) {
            final List<Integer> exact = new ArrayList<>();
            final List<Integer> near = new ArrayList<>();
            for (int column = 0; column < keys.size(); ++column) {
                if (keys.get(column).equals(people.get(person))) {
                    exact.add(column);
                }
                if (Names.compatible(keys.get(column), people.get(person))) {
                    near.add(column);
                }
            }
            final List<Integer> candidates = exact.isEmpty() ? near : exact;
            if (candidates.size() == 1) {
                mapping.put(person, candidates.get(0));
            } else if (candidates.isEmpty()) {
                problems.add(labels.get(person) + ": нет колонки с оценками в сводной таблице");
            } else {
                problems.add(
                    labels.get(person) + ": подходит несколько колонок сводной — "
                        + candidates.stream().map(c -> "«" + Cells.raw(columns.get(c)) + "»")
                            .collect(Collectors.joining(", "))
                );
            }
        }
        final Map<Integer, List<Integer>> claimed = new LinkedHashMap<>();
        for (final Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            claimed.computeIfAbsent(entry.getValue(), c -> new ArrayList<>()).add(entry.getKey());
        }
        for (final Map.Entry<Integer, List<Integer>> entry : claimed.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add(
                    "Колонка «" + Cells.raw(columns.get(entry.getKey()))
                        + "» сводной подходит нескольким студентам: "
                        + entry.getValue().stream().map(labels::get).collect(Collectors.joining(", "))
                );
                entry.getValue().forEach(mapping::remove);
            }
        }
        return mapping;
    }

    private static void fill(
        final Graduate graduate, final String label, final Table grades, final int index,
        final int column, final List<String> problems, final LinkedHashSet<String> rowProblems
    ) {
        // the state exam goes before the thesis, as on the supplement
        for (final List<Object> row : grades.rows) {
            final Object rate = row.get(column);
            if (Cells.blank(rate) || !lower(row.get(index)).startsWith(STATE_EXAM_ROW)) {
                continue;
            }
            final Integer grade = Grades.code(rate);
            if (grade == null) {
                problems.add(
                    label + ": «" + STATE_EXAM + "» — недопустимая оценка «"
                        + Cells.text(rate) + "», допустимы коды 2–7"
                );
                continue;
            }
            graduate.stateExams.add(grade);
        }
        int graded = 0;
        for (final List<Object> row : grades.rows) {
            final Object rate = row.get(column);
            if (Cells.blank(rate)) {
                continue;
            }
            final String full = Cells.text(row.get(index));
            final String lowered = full.toLowerCase(Locale.ROOT);
            if (lowered.startsWith(THESIS_ROW) || lowered.startsWith(STATE_EXAM_ROW)) {
                continue;
            }
            final String[] parts = full.split("_", -1);
            if (parts.length < 3) {
                rowProblems.add(
                    "Строка сводной «" + full + "» содержит оценки, но в названии нет типа "
                        + "и з.е. (ожидается «Название_дисциплина_3»), оценки не попадут в XML"
                );
                continue;
            }
            final String name = parts[0];
            final String type = parts[1];
            ++graded;
            final Integer grade = Grades.code(rate);
            if (grade == null) {
                problems.add(
                    label + ": «" + name + "» — недопустимая оценка «" + Cells.text(rate)
                        + "», допустимы коды 2–7"
                );
                continue;
            }
            final String units = parts[2].strip();
            if (!WHOLE.matcher(units).matches()) {
                rowProblems.add("Строка сводной «" + full + "»: з.е. должны быть целым числом");
                continue;
            }
            final List<String> mixed = Names.mixedScript(name);
            if (!mixed.isEmpty()) {
                rowProblems.add(
                    "Строка сводной «" + full + "»: в названии смешаны латинские и русские буквы ("
                        + String.join(", ", mixed) + ")"
                );
            }
            if (Arrays.asList("дисциплина", "практика", "курсовая", "факультатив", "госэкзамен")
                .contains(type)) {
                graduate.results.add(new Graduate.Result(type, name, grade, Integer.parseInt(units)));
            } else {
                rowProblems.add(
                    "Строка сводной «" + full + "»: неизвестный тип «" + type
                        + "» (ожидается дисциплина, практика, курсовая, факультатив или госэкзамен)"
                );
            }
        }
        if (graded == 0) {
            problems.add(label + ": в сводной нет ни одной оценки");
        }
    }

    private static Object value(final Table table, final List<Object> row, final String title) {
        return row.get(table.column(title));
    }

    private static String lower(final Object value) {
        return Cells.text(value).toLowerCase(Locale.ROOT);
    }
}
