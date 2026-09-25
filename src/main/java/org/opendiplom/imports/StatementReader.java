package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;

/**
 * Reads statements exported from «Деканат»: a sheet per student, the student
 * name in cell E1, column titles on sheet row 7, a row per subject and semester.
 */
public final class StatementReader {
    static final int NAME_COLUMN = 4;
    static final int TITLES_ROW = 6;
    static final String SUBJECT = "наименование предмета";
    static final String HOURS = "часы учр";
    private static final List<String> REQUIRED = Arrays.asList(SUBJECT, HOURS);
    private static final List<String> NOT_SUBJECTS =
        Arrays.asList("ПГТУ -", "Всего", "┌ наименование предмета");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^a-zA-Zа-яА-ЯёЁ\\s]", Pattern.UNICODE_CHARACTER_CLASS);

    private StatementReader() {
    }

    /** Student name and semester rows of one sheet. */
    public static final class StudentSheet {
        private final String sheet;
        private final String student;
        private final List<StatementRow> rows;

        StudentSheet(final String sheet, final String student, final List<StatementRow> rows) {
            this.sheet = sheet;
            this.student = student;
            this.rows = rows;
        }

        public String sheet() {
            return this.sheet;
        }

        public String student() {
            return this.student;
        }

        public List<StatementRow> rows() {
            return this.rows;
        }
    }

    /**
     * @throws WorkbookException a sheet is not in the «Деканат» layout
     */
    public static StudentSheet read(final Sheet sheet) throws WorkbookException {
        final String where = "Ведомость, лист «" + sheet.name() + "»";
        if (sheet.width() <= NAME_COLUMN || sheet.rows().size() <= TITLES_ROW) {
            throw new WorkbookException(
                where + ": лист не похож на ведомость «Деканата» "
                    + "(ожидается ФИО студента в ячейке E1 и заголовки колонок в строке 7)"
            );
        }
        final Object name = sheet.cell(0, NAME_COLUMN);
        if (!(name instanceof String)) {
            throw new WorkbookException(where + ": в ячейке E1 нет ФИО студента");
        }
        final List<Object> titles = new ArrayList<>();
        for (int column = 0; column < sheet.width(); ++column) {
            titles.add(title(sheet.cell(TITLES_ROW, column)));
        }
        final List<String> missing = REQUIRED.stream()
            .filter(required -> !titles.contains(required))
            .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new WorkbookException(
                where + ": в строке 7 нет колонок "
                    + missing.stream().map(t -> "«" + t + "»").collect(Collectors.joining(", "))
                    + ". Ожидается ведомость «Деканата» в обычном макете."
            );
        }
        final Map<String, Integer> columns = new HashMap<>();
        for (int column = titles.size() - 1; column >= 0; --column) {
            final Object title = titles.get(column);
            if (!Cells.missing(title) && !(title instanceof String && ((String) title).contains("дата"))) {
                columns.put(Cells.raw(title), column);
            }
        }
        final List<StatementRow> rows = new ArrayList<>();
        for (int row = TITLES_ROW + 1; row < sheet.rows().size(); ++row) {
            final Object subject = sheet.cell(row, columns.get(SUBJECT));
            final Object spent = sheet.cell(row, columns.get(HOURS));
            if (Cells.missing(subject) || Cells.missing(spent)
                || subject instanceof String && NOT_SUBJECTS.contains(subject)) {
                continue;
            }
            final Double hours = Cells.number(spent);
            if (hours == null) {
                throw new WorkbookException(
                    where + ": у «" + Cells.raw(subject) + "» в колонке «часы уч.р.» не число — «"
                        + Cells.raw(spent) + "»"
                );
            }
            final Map<String, Object> grades = new HashMap<>();
            for (final String column : Arrays.asList(
                StatementRow.TEST, StatementRow.EXAM, StatementRow.COURSE_WORK
            )) {
                if (columns.containsKey(column)) {
                    grades.put(column, sheet.cell(row, columns.get(column)));
                }
            }
            if ("V".equals(grades.get(StatementRow.TEST))) {
                grades.put(StatementRow.TEST, 6.0);
            }
            rows.add(new StatementRow(Cells.raw(subject), hours, grades));
        }
        return new StudentSheet(sheet.name(), (String) name, rows);
    }

    /** Column title with everything but letters and spaces removed. */
    static Object title(final Object value) {
        if (!(value instanceof String)) {
            return value;
        }
        return Cells.collapse(NOT_LETTERS.matcher((String) value).replaceAll(""));
    }
}
