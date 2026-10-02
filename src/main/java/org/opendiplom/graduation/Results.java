package org.opendiplom.graduation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.export.Grades;
import org.opendiplom.imports.Kind;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.imports.StudyRecord;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.sheets.Cells;

/**
 * The results of one student: each subject of the statement on its plan
 * element, named and counted as the plan has it.
 *
 * <p>A course work is printed as its discipline with «(курсовая работа)» or
 * «(курсовой проект)», as the plan has it: so the 2026 files were corrected
 * by hand. An element without credits, like the physical culture electives,
 * does not go to the supplement. A subject in the statement without a grade
 * stays a result without one, for the checks to show.
 */
public final class Results {
    private Results() {
    }

    /** Results in the order of the plan; subjects left out or not matched have none. */
    public static List<ResultRecord> of(final StatementImport statement, final String student, final SubjectMatch match) {
        final List<ResultRecord> results = new ArrayList<>();
        for (final StudyRecord record : statement.records(student)) {
            final SubjectMatch.Target target = match.target(record);
            if (target == null || target.position < 0) {
                continue;
            }
            final PlanItem item = match.plan().items().get(target.position);
            final PlanRow row = item.row();
            final String printed = target.alternative.isEmpty() ? item.printed() : target.alternative;
            final Integer grade = Grades.code(record.grade());
            final String text = Cells.text(record.grade());
            if (record.kind() == Kind.COURSE_WORK) {
                final boolean project = !row.controls().get(PlanRow.COURSE_PROJECTS).isEmpty()
                    && row.controls().get(PlanRow.COURSE_WORKS).isEmpty();
                results.add(new ResultRecord(
                    target.position, ResultRecord.COURSE_WORK,
                    printed + (project ? " (курсовой проект)" : " (курсовая работа)"), grade, text, null
                ));
            } else if (row.credits() != null) {
                results.add(new ResultRecord(target.position, kind(item), printed, grade, text, row.credits()));
            }
        }
        results.sort(Comparator.comparingInt((ResultRecord result) -> result.element)
            .thenComparing(result -> ResultRecord.COURSE_WORK.equals(result.kind)));
        return results;
    }

    private static String kind(final PlanItem item) {
        switch (item.section()) {
            case PRACTICES:
                return ResultRecord.PRACTICE;
            case FACULTATIVES:
                return ResultRecord.FACULTATIVE;
            default:
                return ResultRecord.DISCIPLINE;
        }
    }
}
