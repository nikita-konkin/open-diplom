package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Collections;
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

    private StatementImport(
        final List<String> labels, final Map<String, Map<String, Object>> grades,
        final Map<String, Double> hours, final Map<String, Integer> ungraded
    ) {
        this.labels = Collections.unmodifiableList(labels);
        this.grades = Collections.unmodifiableMap(grades);
        this.hours = Collections.unmodifiableMap(hours);
        this.ungraded = Collections.unmodifiableMap(ungraded);
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
            for (final StudyRecord record : SemesterRecords.of(statement.rows())) {
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
        return new StatementImport(kept, grades, hours, ungraded);
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
}
