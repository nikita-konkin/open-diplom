package org.opendiplom.export;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;

/**
 * The student information file: a row per graduate with personal data, the
 * decision of the ГЭК and the thesis. Every problem is collected; a row whose
 * name cannot be split stays out, a row with other problems stays in with
 * the fields that could be read.
 */
public final class StudentInfo {
    static final List<String> REQUIRED = Arrays.asList(
        "ФИО", "ДатаРожд", "НаименованиеДокПредОбр", "ГодДокПредОбр",
        "ТемаВКР", "НомерПротоколаГэк", "ДатаРешенияГэк", "ОценкаВКР"
    );
    /** Optional: the grade of the state exam, for programs whose attestation has one. */
    public static final String STATE_EXAM = "ОценкаГосэкзамен";
    /** Year of the previous document that is not known yet, like grade 7. */
    public static final String YEAR_PLACEHOLDER = "1111";
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    /** One row of the file. */
    public static final class Entry {
        public final int line;
        public final String fullName;
        public final String lastName;
        public final String firstName;
        public final String middleName;
        /** ГГГГ-ММ-ДД, empty when not read. */
        public final String birthDate;
        public final String previousDocument;
        public final String previousYear;
        /** ГГГГ-ММ-ДД, empty when not read. */
        public final String gekDate;
        public final String gekProtocol;
        public final String thesisTopic;
        public final Integer thesisGrade;
        public final Integer stateExamGrade;
        /** Problems of this row, each with the row named. */
        public final List<String> problems;

        Entry(
            final int line, final String fullName, final List<String> parts, final Map<String, String> texts,
            final Integer thesisGrade, final Integer stateExamGrade, final List<String> problems
        ) {
            this.line = line;
            this.fullName = fullName;
            this.lastName = parts.get(0);
            this.firstName = parts.get(1);
            this.middleName = String.join(" ", parts.subList(2, parts.size()));
            this.birthDate = texts.get("ДатаРожд");
            this.previousDocument = texts.get("НаименованиеДокПредОбр");
            this.previousYear = texts.get("ГодДокПредОбр");
            this.gekDate = texts.get("ДатаРешенияГэк");
            this.gekProtocol = texts.get("НомерПротоколаГэк");
            this.thesisTopic = texts.get("ТемаВКР");
            this.thesisGrade = thesisGrade;
            this.stateExamGrade = stateExamGrade;
            this.problems = Collections.unmodifiableList(problems);
        }

        /** «Фамилия Имя Отчество (строка 5 файла сведений)». */
        public String label() {
            return StudentInfo.label(this.fullName, this.line);
        }

        /** Surname and initials, as a statement names the student. */
        public List<String> key() {
            return Names.person(this.lastName, this.firstName, this.middleName);
        }
    }

    private final List<Entry> entries;
    private final List<String> problems;
    private final boolean stateExams;

    private StudentInfo(final List<Entry> entries, final List<String> problems, final boolean stateExams) {
        this.entries = Collections.unmodifiableList(entries);
        this.problems = Collections.unmodifiableList(problems);
        this.stateExams = stateExams;
    }

    /**
     * @param sheet the first sheet of the file
     * @param today dates and years after it are problems
     * @throws ValidationProblems a required column is missing, or no row is filled in
     */
    public static StudentInfo read(final Sheet sheet, final LocalDate today) throws ValidationProblems {
        final Table students = new Table(sheet);
        final List<String> absent = REQUIRED.stream()
            .filter(title -> students.column(title) < 0)
            .collect(Collectors.toList());
        if (!absent.isEmpty()) {
            throw new ValidationProblems(Arrays.asList(
                "В файле сведений о студентах нет колонок: " + String.join(", ", absent)
            ));
        }
        final int year = today.getYear();
        final boolean stateExams = students.column(STATE_EXAM) >= 0;
        final List<String> problems = new ArrayList<>();
        final List<Entry> entries = new ArrayList<>();
        for (int number = 0; number < students.rows.size(); ++number) {
            final List<Object> row = students.rows.get(number);
            if (row.stream().allMatch(Cells::blank)) {
                continue;
            }
            final int line = students.lines.get(number);
            final String fullName = Cells.text(value(students, row, "ФИО"));
            if (fullName.isEmpty()) {
                problems.add("строка " + line + " файла сведений: не заполнено ФИО");
                continue;
            }
            final String label = label(fullName, line);
            final List<String> own = new ArrayList<>();
            final List<String> mixed = Names.mixedScript(fullName);
            if (!mixed.isEmpty()) {
                own.add(
                    label + ": в ФИО смешаны латинские и русские буквы ("
                        + String.join(", ", mixed) + "), исправьте файл"
                );
            }
            final List<String> parts = Cells.words(fullName);
            if (parts.size() < 2) {
                problems.addAll(own);
                problems.add(label + ": ФИО «" + fullName + "»: нужны как минимум фамилия и имя");
                continue;
            }
            final Map<String, String> texts = new LinkedHashMap<>();
            texts.put("ДатаРожд", date(students, row, "ДатаРожд", 1920, year - 14, label, own));
            texts.put("ДатаРешенияГэк", date(students, row, "ДатаРешенияГэк", 2000, year + 1, label, own));
            final String previous = Cells.text(value(students, row, "ГодДокПредОбр"));
            if (!YEAR_PLACEHOLDER.equals(previous) && (!YEAR.matcher(previous).matches()
                || Integer.parseInt(previous) < 1950 || Integer.parseInt(previous) > year)) {
                own.add(
                    label + ": ГодДокПредОбр должен быть годом из четырёх цифр не позже "
                        + year + " или заглушкой " + YEAR_PLACEHOLDER + ", получено «" + previous + "»"
                );
            }
            texts.put("ГодДокПредОбр", previous);
            for (final String field : Arrays.asList("НаименованиеДокПредОбр", "НомерПротоколаГэк", "ТемаВКР")) {
                texts.put(field, Cells.text(value(students, row, field)));
                if (texts.get(field).isEmpty()) {
                    own.add(label + ": не заполнено поле " + field);
                }
            }
            final Object thesis = value(students, row, "ОценкаВКР");
            final Integer thesisGrade = Grades.code(thesis);
            if (thesisGrade == null) {
                own.add(label + ": ОценкаВКР «" + Cells.text(thesis) + "» — допустимы коды 2–7");
            }
            Integer examGrade = null;
            if (stateExams && !Cells.blank(value(students, row, STATE_EXAM))) {
                final Object exam = value(students, row, STATE_EXAM);
                examGrade = Grades.code(exam);
                if (examGrade == null) {
                    own.add(label + ": " + STATE_EXAM + " «" + Cells.text(exam) + "» — допустимы коды 2–7");
                }
            }
            problems.addAll(own);
            entries.add(new Entry(line, fullName, parts, texts, thesisGrade, examGrade, own));
        }
        if (entries.isEmpty() && problems.isEmpty()) {
            throw new ValidationProblems(Arrays.asList(
                "В файле сведений о студентах нет ни одной заполненной строки"
            ));
        }
        return new StudentInfo(entries, problems, stateExams);
    }

    /** Rows whose name could be split, in the order of the file. */
    public List<Entry> entries() {
        return this.entries;
    }

    /** All problems of the file, in the order of its rows. */
    public List<String> problems() {
        return this.problems;
    }

    /** Whether the file has the column {@link #STATE_EXAM}. */
    public boolean stateExams() {
        return this.stateExams;
    }

    private static String date(
        final Table table, final List<Object> row, final String field, final int first, final int last,
        final String label, final List<String> problems
    ) {
        try {
            return Dates.checked(value(table, row, field), field, first, last);
        } catch (final IllegalArgumentException error) {
            problems.add(label + ": " + error.getMessage());
            return "";
        }
    }

    private static String label(final String fullName, final int line) {
        return fullName + " (строка " + line + " файла сведений)";
    }

    private static Object value(final Table table, final List<Object> row, final String title) {
        return row.get(table.column(title));
    }
}
