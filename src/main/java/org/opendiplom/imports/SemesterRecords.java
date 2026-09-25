package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Joins the semester rows of one student's statement (B-31).
 *
 * <p>A discipline taught over several semesters has a row per semester.
 * Its credits are the hours of all its rows, course work included, over 36;
 * its grade is the last exam grade, or the last test grade when there was no
 * exam. Course work keeps its own record.
 */
public final class SemesterRecords {
    private SemesterRecords() {
    }

    /** Records in the order the subjects first appear in the statement. */
    public static List<StudyRecord> of(final List<StatementRow> rows) {
        final Map<String, List<StatementRow>> bySubject = new LinkedHashMap<>();
        for (final StatementRow row : rows) {
            bySubject.computeIfAbsent(row.subject(), key -> new ArrayList<>()).add(row);
        }
        final List<StudyRecord> records = new ArrayList<>();
        for (final Map.Entry<String, List<StatementRow>> entry : bySubject.entrySet()) {
            final String subject = entry.getKey();
            final List<StatementRow> study = new ArrayList<>();
            final List<StatementRow> works = new ArrayList<>();
            int ungraded = 0;
            for (final StatementRow row : entry.getValue()) {
                final String kind = row.kind();
                if (kind == null) {
                    ++ungraded;
                } else if (StatementRow.COURSE_WORK.equals(kind)) {
                    works.add(row);
                } else {
                    study.add(row);
                }
            }
            if (!study.isEmpty()) {
                Object exam = null;
                Object test = null;
                for (final StatementRow row : study) {
                    if (row.grade(StatementRow.EXAM) != null) {
                        exam = row.grade(StatementRow.EXAM);
                    }
                    if (row.grade(StatementRow.TEST) != null) {
                        test = row.grade(StatementRow.TEST);
                    }
                }
                records.add(new StudyRecord(
                    subject,
                    isPractice(subject) ? Kind.PRACTICE : Kind.DISCIPLINE,
                    hours(entry.getValue()),
                    exam != null ? exam : test,
                    ungraded
                ));
            }
            if (!works.isEmpty()) {
                records.add(new StudyRecord(
                    subject, Kind.COURSE_WORK, hours(works),
                    works.get(works.size() - 1).grade(StatementRow.COURSE_WORK), 0
                ));
            }
            if (study.isEmpty() && works.isEmpty()) {
                // kept without a kind: shows the subject has no grade yet
                records.add(new StudyRecord(subject, Kind.UNKNOWN, 0, null, 0));
            }
        }
        return records;
    }

    static boolean isPractice(final String subject) {
        return subject.toLowerCase(Locale.ROOT).contains("практика");
    }

    private static double hours(final List<StatementRow> rows) {
        double hours = 0;
        for (final StatementRow row : rows) {
            hours += row.hours();
        }
        return hours;
    }
}
