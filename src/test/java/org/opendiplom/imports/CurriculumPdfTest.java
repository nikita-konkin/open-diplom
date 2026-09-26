package org.opendiplom.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.PlanPdfs;
import org.opendiplom.printing.Fonts;
import org.opendiplom.sheets.WorkbookException;

/** A curriculum saved to PDF from «Планы», and the title of a plan in either format. */
final class CurriculumPdfTest {
    private static Path font() {
        final Optional<Path> found = Fonts.serif(null);
        assumeTrue(found.isPresent(), "No Cyrillic serif font on this machine");
        return found.get();
    }

    private static Curriculum plan() throws Exception {
        return Curriculum.read(PlanPdfs.plan(font()));
    }

    @Test
    void cannotLoseCreditsOfNameWrappedToThreeLines() throws Exception {
        assertEquals(
            5.0, plan().credits().get(Curriculum.nameKey(PlanPdfs.WRAPPED_THREE)),
            "A name wrapped around its index lost its lines or its credits"
        );
    }

    @Test
    void cannotLoseCreditsOfNameWrappedToTwoLines() throws Exception {
        assertEquals(
            5.0, plan().credits().get(Curriculum.nameKey(PlanPdfs.WRAPPED_TWO)),
            "A name wrapped to two lines with the index between them was not read"
        );
    }

    @Test
    void cannotTakeCreditsFromColumnOtherThanUnderCreditsTitle() throws Exception {
        assertEquals(
            16.0, plan().credits().get("математика"),
            "Credits were not taken from the first column under «в зачетных единицах»"
        );
    }

    @Test
    void cannotReadFractionalCreditsWrittenWithComma() throws Exception {
        assertEquals(1.5, plan().credits().get("этика"), "«1,5» was not read as one and a half credits");
    }

    @Test
    void cannotReadFacultativeNumberedWithoutLetters() throws Exception {
        assertEquals(3.0, plan().credits().get("теория игр"), "A facultative indexed «1» was lost");
    }

    @Test
    void cannotTakeBlockTotalForElement() throws Exception {
        assertFalse(
            plan().credits().containsKey("блок 1 дисциплины модули"),
            "A block total came in, though a workbook plan does not have it"
        );
    }

    @Test
    void cannotInventCreditsOfElementWithoutThem() throws Exception {
        assertNull(
            plan().credits().get("элективные дисциплины по физической культуре"),
            "An element with an empty credits cell got credits from another column"
        );
    }

    @Test
    void cannotMixDirectionAndProfileFromPdfTitle() throws Exception {
        final PlanTitle title = plan().title();
        assertEquals(
            "09.03.02 ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ / Интеллектуальные информационные системы и технологии",
            title.code() + " " + title.direction() + " / " + title.profile(),
            "The direction and the profile were not read apart from the title (B-25)"
        );
    }

    @Test
    void cannotLoseRestOfPdfTitle() throws Exception {
        final PlanTitle title = plan().title();
        assertEquals(
            "Бакалавр, Очная, 4 года, 2022",
            String.join(", ", title.qualification(), title.studyForm(), title.studyTerm(), title.year()),
            "The qualification, form, term or year was not read from the title"
        );
    }

    @Test
    void cannotSettleStatementWithoutPdfCredits() throws Exception {
        final CreditCheck check = CreditCheck.settle(
            StatementImport.read(Books.statement(student("Тестов Т. Т.", row("Математика", 108, null, 5, null)))),
            plan(),
            "учебный план не загружен"
        ).get(0);
        assertEquals("Математика_дисциплина_16", check.settled(), "Credits of a PDF plan did not reach the subject");
    }

    @Test
    void cannotLoseProgramTotalsOfPdfPlan() throws Exception {
        assertEquals(
            "103 24 9 1500 []", describe(plan().totals()),
            "The program, practices, attestation or contact hours were not read from the totals"
        );
    }

    @Test
    void cannotLoseProgramTotalsOfWorkbookPlan() throws Exception {
        assertEquals(
            "103 24 9 1500 []", describe(Curriculum.read(workbook(1500)).totals()),
            "The totals of a workbook plan were not read"
        );
    }

    @Test
    void cannotTakeContactHoursThatDoNotAddUp() throws Exception {
        final PlanTotals totals = Curriculum.read(workbook(1400)).totals();
        assertNull(totals.contact(), "Contact hours were taken though the hours of the total do not add up");
        assertTrue(
            String.join(" ", totals.problems()).contains("не сходятся"),
            "The operator was not told why contact hours are missing: " + totals.problems()
        );
    }

    /** Program, practices, attestation and contact hours, then the problems. */
    private static String describe(final PlanTotals totals) {
        return Stream.of(totals.program(), totals.practices(), totals.attestation(), totals.contact())
            .map(value -> value == null ? "—" : PlanTotals.number(value))
            .collect(Collectors.joining(" ")) + " " + totals.problems();
    }

    /** A workbook plan whose total has the given contact hours; 1500 makes the hours add up. */
    private static byte[] workbook(final int contact) {
        return Books.book(
            new Object[] {"", PlanPdfs.DIRECTION},
            new Object[] {"№", "Структура ОП", "Кафедра", "Объем частей ОП\nв зачетных единицах",
                "Объем частей ОП\nв часах"},
            new Object[] {"", "", "", "Всего", "Всего", "Экзамены", "Учебные занятия", "Контактная работа",
                "Самостоятельная работа"},
            new Object[] {"Блок 1. Дисциплины (модули)", null, null, 70},
            new Object[] {"Б.1.1.1", "Математика", "Кафедра", 16, 576, 108, 468, 200, 268},
            new Object[] {"Блок 2. Практика", null, null, 24},
            new Object[] {"Блок 3. Государственная итоговая аттестация", null, null, 9},
            new Object[] {"ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", null, null, "Зачетные единицы", "Часы"},
            new Object[] {null, null, null, 103, 3708, 324, 3384, contact, 1884}
        );
    }

    @Test
    void cannotTakeScanForCurriculum() {
        final WorkbookException error = assertThrows(
            WorkbookException.class, () -> Curriculum.read(PlanPdfs.scan()),
            "A PDF without text was taken for a curriculum"
        );
        assertTrue(error.getMessage().contains("скан"), "The operator was not told the PDF is a scan: " + error.getMessage());
    }

    @Test
    void cannotTakePdfWithoutTableForCurriculum() {
        final Path font = font();
        final WorkbookException error = assertThrows(
            WorkbookException.class, () -> Curriculum.read(PlanPdfs.letter(font)),
            "A PDF without the plan table was taken for a curriculum"
        );
        assertTrue(
            error.getMessage().contains("не найдена таблица"),
            "The operator was not told the table is missing: " + error.getMessage()
        );
    }

    @Test
    void cannotAnswerBrokenPdfWithoutExplanation() {
        final WorkbookException error = assertThrows(
            WorkbookException.class,
            () -> Curriculum.read("%PDF-1.7\nобрыв".getBytes(StandardCharsets.UTF_8)),
            "A broken PDF was taken for a curriculum"
        );
        assertTrue(error.getMessage().startsWith("Учебный план:"), "Unclear message: " + error.getMessage());
    }

    @Test
    void cannotMixDirectionAndProfileFromWorkbookTitle() throws Exception {
        final PlanTitle title = Curriculum.read(Books.book(
            new Object[] {"", PlanPdfs.DIRECTION},
            new Object[] {"", "Профиль: (13) \"Интеллектуальные информационные системы и технологии\"",
                "Форма обучения - Очная"},
            new Object[] {"№", "Структура ОП", "Кафедра", "Объем частей ОП\nв зачетных единицах"},
            new Object[] {"", "", "", "Всего"},
            new Object[] {"Б.1.1.1", "Математика", "Кафедра", 16}
        )).title();
        assertEquals(
            "09.03.02 ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ / Интеллектуальные информационные системы и технологии / Очная",
            title.code() + " " + title.direction() + " / " + title.profile() + " / " + title.studyForm(),
            "The title of a workbook plan was not read"
        );
    }

    @Test
    void cannotInventTitleOfPlanWithoutOne() throws Exception {
        assertEquals(
            Collections.emptyMap(),
            Curriculum.read(Books.curriculum(Collections.singletonMap("Математика", 16))).title().fields(),
            "A plan without a title got title fields"
        );
    }
}
