package org.opendiplom.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendiplom.Plans;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;

/** What keeps a graduate from the supplement; the plan is {@link Plans#bachelor()}. */
final class ChecksTest {
    private static final ResultRecord MATHS = new ResultRecord(2, ResultRecord.DISCIPLINE, "Математика", 5, "5", 8.0);
    private static final ResultRecord MATHS_WORK =
        new ResultRecord(2, ResultRecord.COURSE_WORK, "Математика (курсовая работа)", 4, "4", null);
    private static final ResultRecord PHYSICS = new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", 6, "6", 4.0);
    private static final ResultRecord PRACTICE = new ResultRecord(
        10, ResultRecord.PRACTICE, "Производственная практика (преддипломная практика)", 5, "5", 6.0
    );

    private static GraduateRecord graduate(final String year, final Integer exam, final ResultRecord... results) {
        return new GraduateRecord(
            "g", 0, "Иванов", "Иван", "Иванович", "2001-02-03", "Аттестат", year, "2026-06-24", "3", "Тема", 5, exam,
            "Иванов И. И.", "1", "", Arrays.asList(results)
        );
    }

    private static List<String> findings(final GraduateRecord graduate) {
        return findings(graduate, Plans.bachelor());
    }

    private static List<String> findings(final GraduateRecord graduate, final List<PlanRow> rows) {
        final List<String> found = new ArrayList<>();
        for (final Checks.Finding finding : Checks.of(graduate, PlanStructure.of(rows), PlanTotals.of(rows))) {
            found.add(finding.toString());
        }
        return found;
    }

    @Test
    void cannotFindFaultWithCompleteGraduate() {
        assertEquals(
            "[]", findings(graduate("2019", 5, MATHS, MATHS_WORK, PHYSICS, PRACTICE)).toString(),
            "A graduate with every grade of the plan was not ready"
        );
    }

    @Test
    void cannotMissDisciplineWithoutGrade() {
        final List<String> found = findings(graduate("2019", 5, MATHS, MATHS_WORK, PRACTICE));
        assertTrue(
            found.contains("ERROR Б.1.1.2 «Физика»: нет оценки")
                && found.contains("ERROR сумма з.е.: дисциплины и практики 14 + ГИА 3 = 17, а объём программы 21"),
            "A discipline of the plan without a grade, and the credits it leaves out, were not reported: " + found
        );
    }

    @Test
    void cannotMissCourseWorkWithoutGrade() {
        assertEquals(
            "[ERROR Б.1.1.1 «Математика»: нет оценки за курсовую работу]",
            findings(graduate("2019", 5, MATHS, PHYSICS, PRACTICE)).toString(),
            "A course work of the plan without a grade was not reported"
        );
    }

    @Test
    void cannotAskGradeForEachPartOfGradedGroup() {
        final ResultRecord group = new ResultRecord(
            9, ResultRecord.PRACTICE, "Производственная практика (преддипломная практика)", 5, "5", 6.0
        );
        assertEquals(
            "[]", findings(graduate("2019", 5, MATHS, MATHS_WORK, PHYSICS, group)).toString(),
            "A practice graded as a group still asked a grade for its rows"
        );
    }

    @Test
    void cannotTakePlaceholderForReadyGrade() {
        final ResultRecord unknown = new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", 7, "7", 4.0);
        final List<Checks.Finding> found = Checks.of(
            graduate("2019", 5, MATHS, MATHS_WORK, unknown, PRACTICE), PlanStructure.of(Plans.bachelor()),
            PlanTotals.of(Plans.bachelor())
        );
        assertEquals(
            "false true", Checks.ready(found) + " " + Checks.exportable(found),
            "Code 7 «не выполнял» made the graduate ready, or kept the XML from carrying the placeholder"
        );
    }

    @Test
    void cannotAcceptGradeThatIsNoCode() {
        final ResultRecord absent = new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", null, "н/я", 4.0);
        assertEquals(
            "[ERROR «Физика»: недопустимая оценка «н/я», допустимы коды 2–7]",
            findings(graduate("2019", 5, MATHS, MATHS_WORK, absent, PRACTICE)).toString(),
            "A statement mark that is no grade code was accepted"
        );
    }

    @Test
    void cannotRefuseUnknownYearPlaceholder() {
        final List<Checks.Finding> found = Checks.of(
            graduate("1111", 5, MATHS, MATHS_WORK, PHYSICS, PRACTICE), PlanStructure.of(Plans.bachelor()),
            PlanTotals.of(Plans.bachelor())
        );
        assertTrue(
            Checks.ready(found) && found.size() == 1,
            "The placeholder 1111 of an unknown year was more than a warning: " + found
        );
    }

    @Test
    void cannotForgetStateExamOfPlan() {
        assertEquals(
            "[ERROR нет оценки за государственный экзамен: заполните колонку «ОценкаГосэкзамен» файла сведений]",
            findings(graduate("2019", null, MATHS, MATHS_WORK, PHYSICS, PRACTICE)).toString(),
            "A plan with a state exam let a graduate without its grade through"
        );
    }

    @Test
    void cannotAskEveryDisciplineOfElectiveGroup() {
        final List<PlanRow> rows = Plans.bachelor();
        rows.set(3, Plans.row("Б.1.1.2", "Дисциплины по выбору", "", "4 - 4", ""));
        rows.add(4, Plans.row("Б.1.1.2.1", "Физика", ";2;;;", "4 - 4", ""));
        rows.add(5, Plans.row("Б.1.1.2.2", "Астрономия", ";2;;;", "4 - 4", ""));
        final ResultRecord physics = new ResultRecord(4, ResultRecord.DISCIPLINE, "Физика", 6, "6", 4.0);
        final ResultRecord practice = new ResultRecord(
            12, ResultRecord.PRACTICE, "Производственная практика (преддипломная практика)", 5, "5", 6.0
        );
        assertEquals(
            "[]", findings(graduate("2019", 5, MATHS, MATHS_WORK, physics, practice), rows).toString(),
            "A graduate who chose one discipline of an elective group was asked a grade for the other"
        );
    }
}
