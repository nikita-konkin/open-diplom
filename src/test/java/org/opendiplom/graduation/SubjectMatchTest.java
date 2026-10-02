package org.opendiplom.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.Plans;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;

/** Statement subjects against plan elements. */
final class SubjectMatchTest {
    /** Block 1 with an elective, a facultative, a practice of two parts and the attestation. */
    static List<PlanRow> plan() {
        return new ArrayList<>(Arrays.asList(
            Plans.row("", "Блок 1. Дисциплины (модули)", "", "13 - 13", ""),
            Plans.row("Б.1.1", "Обязательная часть", "", "13 - 13", ""),
            Plans.row("Б.1.1.1", "Математика", "1;;;;1", "8 - 8", ""),
            Plans.row("Б.1.1.2", "Теоретические основы электротехники", "2;;;;", "4 - 4", ""),
            Plans.row("Б.1.1.3", "Элективная дисциплина 1 (Основы видеоаналитики / Умный дом)", ";3;;;", "1 - 1", ""),
            Plans.row("Б.1.1.4", "Элективные дисциплины по физической культуре и спорту (Общая физическая "
                + "подготовка / Спортивные секции)", ";1,2;;;", "- - -", ""),
            Plans.row("", "Факультативные дисциплины", "", "- - -", ""),
            Plans.row("1", "Теория игр", ";3;;;", "2 - 2", ""),
            Plans.row("", "Блок 2. Практика", "", "9 - 9", ""),
            Plans.row("Б.2.1", "Обязательная часть", "", "9 - 9", ""),
            Plans.row("Б.2.1.1", "Производственная практика", ";;4,6;;", "9 - 9", ""),
            Plans.row("Б.2.1.1.1", "Технологическая практика (рассредоточенная)", ";;4;;", "3 - 3", ""),
            Plans.row("Б.2.1.1.2", "Технологическая практика", ";;6;;", "6 - 6", ""),
            Plans.row("", "Блок 3. Государственная итоговая аттестация", "", "3 - 3", ""),
            Plans.row("Б.3.1", "Обязательная часть", "", "3 - 3", ""),
            Plans.row("Б.3.1.1", "Выполнение и защита выпускной квалификационной работы", "", "3 - 3", ""),
            Plans.row("", "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", "", "25 - 25", "")
        ));
    }

    static StatementImport statement(final Object[]... rows) throws Exception {
        return StatementImport.read(Books.statement(student("Андреев А. А.", rows)));
    }

    static SubjectMatch match(final StatementImport statement, final Map<String, SubjectMatch.Link> links,
        final Map<String, String> choices) {
        return SubjectMatch.of(statement, statement.students(), PlanStructure.of(plan()), links, choices);
    }

    /** «Статус → индекс и печатное имя» of the subject of a name. */
    static String found(final SubjectMatch match, final String subject) {
        for (final SubjectMatch.Row row : match.rows()) {
            if (row.subject.name.equals(subject)) {
                if (row.target == null) {
                    return row.status.toString();
                }
                if (row.target.position < 0) {
                    return row.status + " → нигде";
                }
                final PlanRow element = match.plan().items().get(row.target.position).row();
                return row.status + " → " + element.index() + " "
                    + (row.target.alternative.isEmpty() ? match.plan().items().get(row.target.position).printed()
                        : row.target.alternative);
            }
        }
        throw new AssertionError("No subject «" + subject + "»");
    }

    @Test
    void cannotMissSameName() throws Exception {
        assertEquals(
            "EXACT → Б.1.1.1 Математика",
            found(match(statement(row("Математика", 288, null, 5, null)), Map.of(), Map.of()), "Математика"),
            "A subject named as a plan element was not matched to it"
        );
    }

    @Test
    void cannotLoseElectiveChosen() throws Exception {
        assertEquals(
            "EXACT → Б.1.1.3 Умный дом",
            found(match(statement(row("Умный дом (Элективная дисциплина 1)", 36, 5, null, null)), Map.of(), Map.of()),
                "Умный дом (Элективная дисциплина 1)"),
            "The elective chosen was not found in the brackets of the plan element"
        );
    }

    @Test
    void cannotSplitPracticeGradedAsWhole() throws Exception {
        assertEquals(
            "EXACT → Б.2.1.1 Производственная практика (технологическая практика)",
            found(match(statement(row("Производственная практика. Технологическая практика", 324, 5, null, null)),
                Map.of(), Map.of()), "Производственная практика. Технологическая практика"),
            "A practice graded for both parts together, 9 credits, was not matched to the group of 9"
        );
    }

    @Test
    void cannotTakeGroupForItsPart() throws Exception {
        assertEquals(
            "EXACT → Б.2.1.1.2 Производственная практика (технологическая практика)",
            found(match(statement(row("Производственная практика. Технологическая практика", 216, 5, null, null)),
                Map.of(), Map.of()), "Производственная практика. Технологическая практика"),
            "A practice of 6 credits was matched to the group of 9 instead of its part"
        );
    }

    @Test
    void cannotMissNameInsideLongerOne() throws Exception {
        assertEquals(
            "CONTAINED → Б.1.1.2 Теоретические основы электротехники",
            found(match(statement(row("Основы электротехники", 144, null, 4, null)), Map.of(), Map.of()),
                "Основы электротехники"),
            "The only element containing the subject name was not matched"
        );
    }

    @Test
    void cannotMatchSimilarNameWithoutOperator() throws Exception {
        final SubjectMatch match = match(
            statement(row("Основы электротехники теоретические", 144, null, 4, null)), Map.of(), Map.of()
        );
        assertEquals(
            "PROPOSED → Б.1.1.2 Теоретические основы электротехники false",
            found(match, "Основы электротехники теоретические") + " " + match.resolved(),
            "A name with the same words in another order was matched without the operator confirming it"
        );
    }

    @Test
    void cannotConfirmSubjectMissingFromPlan() throws Exception {
        final SubjectMatch match = match(statement(row("Астрономия", 72, "V", null, null)), Map.of(), Map.of());
        assertEquals(
            "NONE false", found(match, "Астрономия") + " " + match.resolved(),
            "A subject that is not in the plan was let through"
        );
    }

    @Test
    void cannotForgetOperatorsLink() throws Exception {
        final StatementImport statement = statement(row("Астрономия", 72, "V", null, null));
        final SubjectMatch.Target chosen = new SubjectMatch.Target(3, "");
        final SubjectMatch.Link link = SubjectMatch.Link.of(
            "дисциплина:астрономия", PlanStructure.of(plan()), chosen
        );
        // the next year's plan has the element under another position and index
        final List<PlanRow> next = plan();
        next.add(2, Plans.row("Б.1.1.1", "Философия", ";1;;;", "2 - 2", ""));
        next.set(4, Plans.row("Б.1.1.3", "Теоретические основы электротехники", "2;;;;", "4 - 4", ""));
        final SubjectMatch match = SubjectMatch.of(
            statement, statement.students(), PlanStructure.of(next), Map.of(link.subjectKey, link), Map.of()
        );
        assertEquals(
            "REMEMBERED → Б.1.1.3 Теоретические основы электротехники", found(match, "Астрономия"),
            "A link the operator confirmed was not found in the plan of the next year"
        );
    }

    @Test
    void cannotIgnoreOperatorsChoice() throws Exception {
        final SubjectMatch match = match(
            statement(row("Астрономия", 72, "V", null, null)), Map.of(),
            Map.of(SubjectMatch.PREFIX + "дисциплина:астрономия", "-")
        );
        assertTrue(
            match.resolved() && "EXCLUDED → нигде".equals(found(match, "Астрономия")),
            "A subject the operator left out of the supplement still waits for a choice"
        );
    }

    @Test
    void cannotPutTwoSubjectsOfStudentOnOneElement() throws Exception {
        final SubjectMatch match = match(
            statement(row("Математика", 288, null, 5, null), row("Высшая математика", 72, null, 4, null)), Map.of(),
            Map.of()
        );
        assertEquals(
            "CONFLICT → Б.1.1.1 Математика / CONFLICT → Б.1.1.1 Математика",
            found(match, "Математика") + " / " + found(match, "Высшая математика"),
            "Two subjects of one student went to one element and one would hide the other"
        );
    }

    @Test
    void cannotTakeCourseWorkForDiscipline() throws Exception {
        final SubjectMatch match = match(
            statement(row("Математика", 252, null, 5, null), row("Математика", 36, null, null, 4)), Map.of(), Map.of()
        );
        assertEquals(
            2, match.rows().stream().filter(row -> row.status == SubjectMatch.Status.EXACT).count(),
            "A discipline and its course work did not both find the element"
        );
        assertFalse(match.rows().stream().anyMatch(row -> row.status == SubjectMatch.Status.CONFLICT),
            "A course work conflicted with its own discipline");
    }

    @Test
    void cannotKeepElectiveNoteInVariants() {
        assertTrue(
            SubjectMatch.variants("Умный дом (Элективная дисциплина 1)").contains("умный дом"),
            "The note «(Элективная дисциплина 1)» was not dropped from the name"
        );
    }
}
