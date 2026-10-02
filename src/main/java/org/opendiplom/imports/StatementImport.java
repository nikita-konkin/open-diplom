package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;

/**
 * Grades of a group from a «Деканат» statement workbook, before they are
 * matched to the curriculum: a label per subject and kind of work, a column
 * per student.
 */
public final class StatementImport {
    private final List<String> labels;
    private final Map<String, Map<String, Object>> grades;
    private final Map<String, Double> hours;
    private final Map<String, Integer> ungraded;
    private final Map<String, StatementReader.StudentSheet> sheets;
    private final Map<String, List<StudyRecord>> records;

    private StatementImport(
        final List<String> labels, final Map<String, Map<String, Object>> grades,
        final Map<String, Double> hours, final Map<String, Integer> ungraded,
        final Map<String, StatementReader.StudentSheet> sheets, final Map<String, List<StudyRecord>> records
    ) {
        this.labels = Collections.unmodifiableList(labels);
        this.grades = Collections.unmodifiableMap(grades);
        this.hours = Collections.unmodifiableMap(hours);
        this.ungraded = Collections.unmodifiableMap(ungraded);
        this.sheets = Collections.unmodifiableMap(sheets);
        this.records = Collections.unmodifiableMap(records);
    }

    /**
     * @throws WorkbookException the file is not a statement, or a student is on two sheets
     */
    public static StatementImport read(final byte[] content) throws WorkbookException {
        return of(Workbooks.read(content, "Ведомость"));
    }

    public static StatementImport of(final List<Sheet> sheets) throws WorkbookException {
        final Map<String, Boolean> labels = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> grades = new TreeMap<>();
        final Map<String, Double> hours = new LinkedHashMap<>();
        final Map<String, Integer> ungraded = new LinkedHashMap<>();
        final Map<String, StatementReader.StudentSheet> read = new HashMap<>();
        final Map<String, List<StudyRecord>> records = new HashMap<>();
        for (final Sheet sheet : sheets) {
            final StatementReader.StudentSheet statement = StatementReader.read(sheet);
            if (grades.containsKey(statement.student())) {
                throw new WorkbookException(
                    "Ведомость, лист «" + sheet.name() + "»: студент «" + statement.student()
                        + "» уже есть на другом листе — у однофамильцев должны различаться инициалы"
                );
            }
            final Map<String, Object> own = new LinkedHashMap<>();
            grades.put(statement.student(), own);
            read.put(statement.student(), statement);
            records.put(statement.student(), SemesterRecords.of(statement.rows()));
            for (final StudyRecord record : records.get(statement.student())) {
                final String label = record.label();
                labels.putIfAbsent(label, Boolean.TRUE);
                own.put(label, record.grade());
                hours.putIfAbsent(label, record.hours());
                if (record.ungraded() > 0) {
                    ungraded.merge(label, 1, Integer::sum);
                }
            }
        }
        // A student with no grade yet gives a subject a label without a kind;
        // when other students have the typed label, both are the same subject.
        final Set<String> typed = new HashSet<>();
        for (final String label : labels.keySet()) {
            final String name = Credits.nameOf(label);
            if (name != null) {
                typed.add(name);
            }
        }
        final List<String> kept = new ArrayList<>();
        for (final String label : labels.keySet()) {
            if (Credits.nameOf(label) == null && typed.contains(label)) {
                for (final Map<String, Object> values : grades.values()) {
                    values.remove(label);
                }
            } else {
                kept.add(label);
            }
        }
        return new StatementImport(kept, grades, hours, ungraded, read, records);
    }

    /** Labels «Название_тип_з.е.» in the order subjects first appear. */
    public List<String> labels() {
        return this.labels;
    }

    /** Student names from cell E1, sorted. */
    public List<String> students() {
        return new ArrayList<>(this.grades.keySet());
    }

    /** Grade of a student for a label, {@code null} when there is none. */
    public Object grade(final String student, final String label) {
        final Map<String, Object> values = this.grades.get(student);
        return values == null ? null : values.get(label);
    }

    /** Statement hours of a label, taken from the first student who has it. */
    public Double hours(final String label) {
        return this.hours.get(label);
    }

    /** Number of students with a semester of the label that has no grade. */
    public int ungraded(final String label) {
        return this.ungraded.getOrDefault(label, 0);
    }

    /** The sheet of a student: its name, study form, number and admission year. */
    public StatementReader.StudentSheet sheet(final String student) {
        return this.sheets.get(student);
    }

    /** Subjects of a student with all their semesters joined, in the order of the statement. */
    public List<StudyRecord> records(final String student) {
        return this.records.getOrDefault(student, Collections.emptyList());
    }

    /** The study form of most sheets, empty when no sheet has one. */
    public String form() {
        final List<Object> forms = new ArrayList<>();
        for (final StatementReader.StudentSheet sheet : this.sheets.values()) {
            if (!sheet.form().isEmpty()) {
                forms.add(sheet.form());
            }
        }
        final Object form = common(forms);
        return form == null ? "" : (String) form;
    }

    /** The admission year of most sheets, {@code null} when no sheet gives one. */
    public Integer admissionYear() {
        final List<Object> years = new ArrayList<>();
        for (final StatementReader.StudentSheet sheet : this.sheets.values()) {
            if (sheet.admission() != null) {
                years.add(sheet.admission());
            }
        }
        return (Integer) common(years);
    }

    /** Sheets whose study form or admission year differ from the rest of the group, or are missing. */
    public List<String> headerProblems() {
        final List<String> problems = new ArrayList<>();
        final String form = this.form();
        final Integer year = this.admissionYear();
        for (final String student : this.students()) {
            final StatementReader.StudentSheet sheet = this.sheets.get(student);
            final String where = "Лист «" + sheet.sheet() + "» (" + student + ")";
            if (sheet.form().isEmpty()) {
                problems.add(where + ": в ячейке C1 нет формы обучения");
            } else if (!sheet.form().equals(form)) {
                problems.add(where + ": форма обучения «" + sheet.form() + "», у группы «" + form + "»");
            }
            if (sheet.admission() == null) {
                problems.add(where + ": не найдены курс и даты первой сессии, год набора не определён");
            } else if (!sheet.admission().equals(year)) {
                problems.add(where + ": год набора " + sheet.admission() + " по первой сессии, у группы " + year);
            }
        }
        return problems;
    }

    /** The value most values have; of equally common ones, the first. */
    private static Object common(final List<Object> values) {
        final Map<Object, Integer> counts = new LinkedHashMap<>();
        for (final Object value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        Object found = null;
        for (final Map.Entry<Object, Integer> entry : counts.entrySet()) {
            if (found == null || entry.getValue() > counts.get(found)) {
                found = entry.getKey();
            }
        }
        return found;
    }
}
