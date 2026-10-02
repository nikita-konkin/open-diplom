package org.opendiplom.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.plans.PlanStructure;

/** Results of a student as the supplement prints them. */
final class ResultsTest {
    private static List<String> results(final String student, final Object[]... students) throws Exception {
        final StatementImport statement = StatementImport.read(Books.statement(students));
        final SubjectMatch match = SubjectMatch.of(
            statement, statement.students(), PlanStructure.of(SubjectMatchTest.plan()), Map.of(), Map.of()
        );
        final List<String> results = new ArrayList<>();
        for (final ResultRecord result : Results.of(statement, student, match)) {
            results.add(result.element + " " + result.kind + " «" + result.printed + "» " + result.grade + " "
                + result.credits);
        }
        return results;
    }

    @Test
    void cannotPrintCourseWorkAsDiscipline() throws Exception {
        assertEquals(
            "[2 дисциплина «Математика» 5 8.0, 2 курсовая «Математика (курсовая работа)» 4 null]",
            results("Андреев А. А.", student("Андреев А. А.",
                row("Математика", 252, null, 5, null), row("Математика", 36, null, null, 4)
            )).toString(),
            "A course work was not named as the plan has it, or took the credits of its discipline"
        );
    }

    @Test
    void cannotPrintElectiveGroupInsteadOfChoice() throws Exception {
        assertEquals(
            "[4 дисциплина «Умный дом» 5 1.0]",
            results("Андреев А. А.", student("Андреев А. А.", row("Умный дом (Элективная дисциплина 1)", 36, 5, null, null)))
                .toString(),
            "The supplement would print the elective group instead of the discipline chosen"
        );
    }

    @Test
    void cannotPrintElementWithoutCredits() throws Exception {
        assertEquals(
            "[]",
            results("Андреев А. А.", student("Андреев А. А.", row(
                "Общая физическая подготовка (Элективные дисциплины по физической культуре и спорту)", 328, "V", null, null
            ))).toString(),
            "A physical culture elective, outside the credits of the plan, went to the supplement"
        );
    }

    @Test
    void cannotHideSubjectWithoutGrade() throws Exception {
        assertEquals(
            "[7 факультатив «Теория игр» null 2.0]",
            results("Борисов Б. Б.",
                student("Андреев А. А.", row("Теория игр", 72, "V", null, null)),
                student("Борисов Б. Б.", row("Теория игр", 72, null, null, null))
            ).toString(),
            "A facultative the statement lists without a grade vanished instead of waiting for one"
        );
    }
}
