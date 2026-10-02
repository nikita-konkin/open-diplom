package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;

/**
 * Reads statements exported from «Деканат»: a sheet per student, the student
 * name in cell E1, column titles on sheet row 7, a row per subject and semester.
 *
 * <p>The first row also gives the study form (C1) and the student number
 * (H1, «№ 3210301000»); under it the first semester has its course and the
 * dates of its session, which give the admission year.
 */
public final class StatementReader {
    static final int NAME_COLUMN = 4;
    static final int FORM_COLUMN = 2;
    static final int NUMBER_COLUMN = 7;
    private static final String COURSE = "курс";
    private static final String SESSION = "сессия";
    private static final Pattern DATE = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    /** An academic year starts in the autumn: a session from July on belongs to the year it starts in. */
    private static final int FIRST_MONTH = 7;
    static final int TITLES_ROW = 6;
    static final String SUBJECT = "наименование предмета";
    static final String HOURS = "часы учр";
    private static final List<String> REQUIRED = Arrays.asList(SUBJECT, HOURS);
    private static final List<String> NOT_SUBJECTS =
        Arrays.asList("ПГТУ -", "Всего", "┌ наименование предмета");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^a-zA-Zа-яА-ЯёЁ\\s]", Pattern.UNICODE_CHARACTER_CLASS);

    private StatementReader() {
    }

    /** Student name, the header and the semester rows of one sheet. */
    public static final class StudentSheet {
        private final String sheet;
        private final String student;
        private final String form;
        private final String number;
        private final Integer admission;
        private final List<StatementRow> rows;

        StudentSheet(
            final String sheet, final String student, final String form, final String number,
            final Integer admission, final List<StatementRow> rows
        ) {
            this.sheet = sheet;
            this.student = student;
            this.form = form;
            this.number = number;
            this.admission = admission;
            this.rows = rows;
        }

        public String sheet() {
            return this.sheet;
        }

        public String student() {
            return this.student;
        }

        /** «очная», «заочная», «очно-заочная»; empty when C1 is empty. */
        public String form() {
            return this.form;
        }

        /** Student number without «№», empty when H1 is empty. */
        public String number() {
            return this.number;
        }

        /** Admission year from the first session and its course, {@code null} when not found. */
        public Integer admission() {
            return this.admission;
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
        return new StudentSheet(
            sheet.name(), (String) name, Cells.text(sheet.cell(0, FORM_COLUMN)).toLowerCase(Locale.ROOT),
            Cells.text(sheet.cell(0, NUMBER_COLUMN)).replaceFirst("^№\\s*", ""), admission(sheet), rows
        );
    }

    /**
     * The year of the academic year of the first session less the years
     * before its course: a student who came in the second course in 2022 was
     * admitted in 2021, as the rest of the group.
     */
    static Integer admission(final Sheet sheet) {
        for (int row = 0; row < TITLES_ROW; ++row) {
            if (!COURSE.equals(title(sheet.cell(row, 0)))) {
                continue;
            }
            final Double course = Cells.number(sheet.cell(row + 1, 0));
            for (int column = 0; column < sheet.width(); ++column) {
                if (!SESSION.equals(title(sheet.cell(row, column)))) {
                    continue;
                }
                final Matcher date = DATE.matcher(Cells.text(sheet.cell(row + 1, column)));
                if (course == null || course < 1 || !date.find()) {
                    return null;
                }
                final int year = Integer.parseInt(date.group(3));
                final int start = Integer.parseInt(date.group(2)) >= FIRST_MONTH ? year : year - 1;
                return start - course.intValue() + 1;
            }
            return null;
        }
        return null;
    }

    /** Column title with everything but letters and spaces removed. */
    static Object title(final Object value) {
        if (!(value instanceof String)) {
            return value;
        }
        return Cells.collapse(NOT_LETTERS.matcher((String) value).replaceAll(""));
    }
}
