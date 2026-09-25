package org.opendiplom.imports;

import java.util.Map;
import org.opendiplom.sheets.Cells;

/** One semester row of a statement: a subject, its hours and its grades. */
public final class StatementRow {
    static final String TEST = "зачет";
    static final String EXAM = "экзамен";
    static final String COURSE_WORK = "курсовой";

    private final String subject;
    private final double hours;
    private final Map<String, Object> grades;

    /**
     * @param subject subject name as written in the statement
     * @param hours study hours of the semester
     * @param grades values of the columns «зачет», «экзамен», «курсовой»
     */
    public StatementRow(final String subject, final double hours, final Map<String, Object> grades) {
        this.subject = subject;
        this.hours = hours;
        this.grades = grades;
    }

    public String subject() {
        return this.subject;
    }

    public double hours() {
        return this.hours;
    }

    /** Grade in a column, {@code null} when the cell is empty or the column is absent. */
    public Object grade(final String column) {
        final Object value = this.grades.get(column);
        return Cells.missing(value) ? null : value;
    }

    /** Form of control of the row, {@code null} when the row has no grade. */
    String kind() {
        if (this.grade(TEST) != null) {
            return TEST;
        }
        if (SemesterRecords.isPractice(this.subject)) {
            // a practice row may still wait for its grade
            return "практика";
        }
        if (this.grade(EXAM) != null) {
            return EXAM;
        }
        if (this.grade(COURSE_WORK) != null) {
            return COURSE_WORK;
        }
        return null;
    }
}
