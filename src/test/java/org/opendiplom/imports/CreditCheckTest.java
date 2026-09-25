package org.opendiplom.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.sheets.WorkbookException;

final class CreditCheckTest {
    private static final Object[] TWO_SEMESTERS = student("Тестов Т. Т.",
        row("Математика", 72, "V", null, null),
        row("Математика", 108, null, 5, null)
    );

    private static Map<String, Object> plan(final String name, final Object credits) {
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put(name, credits);
        return plan;
    }

    private static CreditCheck check(final Object[] student, final byte[] plan) throws WorkbookException {
        final List<CreditCheck> checks = CreditCheck.settle(
            StatementImport.read(Books.statement(student)),
            plan == null ? null : Curriculum.read(plan),
            "учебный план не загружен"
        );
        return checks.get(0);
    }

    @Test
    void cannotIgnoreCurriculumCredits() throws Exception {
        assertEquals(
            "Математика_дисциплина_6",
            check(TWO_SEMESTERS, Books.curriculum(plan("Математика", 6))).settled(),
            "Credits from the curriculum did not reach the subject"
        );
    }

    @Test
    void cannotReadOldCurriculumFormat() throws Exception {
        assertEquals(
            "Математика_дисциплина_6",
            check(TWO_SEMESTERS, Books.curriculum(
                plan("Математика", 6), "Наименование дисциплин", "ТРУДОЕМКОСТЬ В ЗАЧЕТНЫХ ЕДИНИЦАХ"
            )).settled(),
            "Credits from a curriculum in the old format were not read"
        );
    }

    @Test
    void cannotHideCreditsMissingFromCurriculum() throws Exception {
        assertEquals(
            "нет в учебном плане",
            check(TWO_SEMESTERS, Books.curriculum(plan("Физика", 9))).notes(),
            "A subject absent from the curriculum was not marked for checking"
        );
    }

    @Test
    void cannotHideMissingCurriculum() throws Exception {
        assertEquals(
            "учебный план не загружен",
            check(TWO_SEMESTERS, null).notes(),
            "Credits counted without a curriculum were not marked for checking"
        );
    }

    @Test
    void cannotHideFractionalPlanCredits() throws Exception {
        assertEquals(
            "в учебном плане дробные з.е. (4.5)",
            check(TWO_SEMESTERS, Books.curriculum(plan("Математика", 4.5))).notes(),
            "Fractional credits in the curriculum were used without a note"
        );
    }

    @Test
    void cannotHideHoursThatAreNotWholeCredits() throws Exception {
        assertEquals(
            "учебный план не загружен; часы ведомости не кратны 36 (100 ч)",
            check(student("Тестов Т. Т.", row("Физика", 100, null, 4, null)), null).notes(),
            "Hours that are not whole credits were rounded silently"
        );
    }

    @Test
    void cannotMissPlanElementNamedMoreFully() throws Exception {
        assertEquals(
            "Иностранный язык_дисциплина_7",
            check(
                student("Тестов Т. Т.", row("Иностранный язык", 72, 5, null, null)),
                Books.curriculum(plan("Иностранный язык (английский)", 7))
            ).settled(),
            "A plan element whose name adds a clarification was not found"
        );
    }

    @Test
    void cannotWarnWhenHoursAgreeWithPlan() throws Exception {
        assertEquals(
            "",
            check(TWO_SEMESTERS, Books.curriculum(plan("Математика", 5))).notes(),
            "Agreeing hours and curriculum still produced a note"
        );
    }

    @Test
    void cannotAcceptWorkbookWithoutCreditColumnsAsCurriculum() {
        assertThrows(
            WorkbookException.class,
            () -> Curriculum.read(Books.book(new Object[] {"Дисциплина", "Часы"}, new Object[] {"Физика", 108})),
            "A workbook without credit columns was taken for a curriculum"
        );
    }
}
