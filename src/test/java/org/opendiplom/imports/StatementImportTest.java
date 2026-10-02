package org.opendiplom.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.WorkbookException;

final class StatementImportTest {
    private static final Object[] TWO_SEMESTERS = student("Тестов Т. Т.",
        row("Математика", 72, "V", null, null),
        row("Математика", 108, null, 5, null)
    );

    private static StatementImport read(final byte[] content) throws WorkbookException {
        return StatementImport.read(content);
    }

    @Test
    void cannotCountCreditsOfOneSemester() throws Exception {
        assertTrue(
            read(Books.statement(TWO_SEMESTERS)).labels().contains("Математика_дисциплина_5"),
            "Credits of a two-semester discipline were taken from one semester"
        );
    }

    @Test
    void cannotPreferFirstSemesterGradeToExam() throws Exception {
        assertEquals(
            "5", Cells.text(read(Books.statement(TWO_SEMESTERS)).grade("Тестов Т. Т.", "Математика_дисциплина_5")),
            "The grade of a first-semester test replaced the exam grade"
        );
    }

    @Test
    void cannotPreferLaterTestToExam() throws Exception {
        final StatementImport statement = read(Books.statement(student("Тестов Т. Т.",
            row("Физика", 252, null, 3, null),
            row("Физика", 72, 4, null, null)
        )));
        assertEquals(
            "3", Cells.text(statement.grade("Тестов Т. Т.", "Физика_дисциплина_9")),
            "A later graded test replaced the exam grade"
        );
    }

    @Test
    void cannotLeaveCourseWorkHoursOutOfCredits() throws Exception {
        final StatementImport statement = read(Books.statement(student("Тестов Т. Т.",
            row("Радиоприемные устройства", 80, null, 4, null),
            row("Радиоприемные устройства", 100, null, null, 5)
        )));
        assertTrue(
            statement.labels().contains("Радиоприемные устройства_дисциплина_5"),
            "Course work hours were not counted in the discipline credits"
        );
    }

    @Test
    void cannotMergeCourseWorkIntoDiscipline() throws Exception {
        final StatementImport statement = read(Books.statement(student("Тестов Т. Т.",
            row("Радиоприемные устройства", 80, null, 4, null),
            row("Радиоприемные устройства", 100, null, null, 5)
        )));
        assertEquals(
            "5", Cells.text(statement.grade("Тестов Т. Т.", "Радиоприемные устройства_курсовая_3")),
            "Course work lost its own record and grade"
        );
    }

    @Test
    void cannotLoseGradesWhenFirstStudentHasNone() throws Exception {
        final StatementImport statement = read(Books.statement(
            student("Андреев А. А.", row("Математика", 108, null, null, null)),
            student("Борисов Б. Б.", row("Математика", 108, null, 4, null))
        ));
        assertEquals(
            "4", Cells.text(statement.grade("Борисов Б. Б.", "Математика_дисциплина_3")),
            "A grade was lost because the first student had none yet"
        );
    }

    @Test
    void cannotKeepUntypedLabelNextToTypedOne() throws Exception {
        final StatementImport statement = read(Books.statement(
            student("Андреев А. А.", row("Математика", 108, null, null, null)),
            student("Борисов Б. Б.", row("Математика", 108, null, 4, null))
        ));
        assertEquals(
            Arrays.asList("Математика_дисциплина_3"), statement.labels(),
            "A subject without grades yet became a second row"
        );
    }

    @Test
    void cannotReadPassedTestAsLetter() throws Exception {
        final StatementImport statement = read(Books.statement(student("Тестов Т. Т.",
            row("Философия", 72, "V", null, null)
        )));
        assertEquals(
            "6", Cells.text(statement.grade("Тестов Т. Т.", "Философия_дисциплина_2")),
            "«V» in the test column did not become code 6 («зачтено»)"
        );
    }

    @Test
    void cannotRejectOldXlsStatement() throws Exception {
        assertTrue(
            read(Books.oldStatement(TWO_SEMESTERS)).labels().contains("Математика_дисциплина_5"),
            "A statement in the old .xls format was not read"
        );
    }

    @Test
    void cannotAcceptTextFileAsWorkbook() {
        final WorkbookException error = assertThrows(
            WorkbookException.class,
            () -> read("this is not an xlsx archive".getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(
            error.getMessage().contains("не является книгой Excel"),
            "A text file was not refused as a workbook"
        );
    }

    @Test
    void cannotCrashOnForeignStatementLayout() {
        final WorkbookException error = assertThrows(
            WorkbookException.class,
            () -> read(Books.statement(
                new XSSFWorkbook(), Arrays.asList("Предмет", "Часы"), student("Тестов Т. Т.")
            ))
        );
        assertTrue(
            error.getMessage().contains("в строке 7 нет колонок «наименование предмета», «часы учр»"),
            "A statement in another layout gave an unexplained error"
        );
    }

    @Test
    void cannotAcceptHoursThatAreNotNumbers() {
        final WorkbookException error = assertThrows(
            WorkbookException.class,
            () -> read(Books.statement(student("Тестов Т. Т.",
                row("Математика", "сто восемь", null, 5, null)
            )))
        );
        assertTrue(
            error.getMessage().contains("у «Математика» в колонке «часы уч.р.» не число"),
            "Non-numeric hours gave an unexplained error"
        );
    }

    @Test
    void cannotMixUpTwoSheetsOfOneStudent() {
        final WorkbookException error = assertThrows(
            WorkbookException.class,
            () -> read(Books.statement(TWO_SEMESTERS, TWO_SEMESTERS))
        );
        assertTrue(
            error.getMessage().contains("уже есть на другом листе"),
            "Two sheets of the same student were merged silently"
        );
    }

    @Test
    void cannotTakeTotalRowForSubject() throws Exception {
        final StatementImport statement = read(Books.statement(student("Тестов Т. Т.",
            row("Математика", 108, null, 5, null),
            row("Всего", 108, null, null, null)
        )));
        assertFalse(
            statement.labels().stream().anyMatch(label -> label.startsWith("Всего")),
            "The total row of a statement became a subject"
        );
    }

    @Test
    void cannotMissStudyFormAndAdmissionYearOfGroup() throws Exception {
        final StatementImport statement = read(Books.statement(
            student("Андреев А. А.", "Заочная", 1, "25.08.2021 - 06.02.2022", row("Математика", 108, null, 4, null)),
            student("Борисов Б. Б.", "Заочная", 1, "25.08.2021 - 06.02.2022", row("Математика", 108, null, 5, null))
        ));
        assertEquals(
            "заочная 2021 []", statement.form() + " " + statement.admissionYear() + " " + statement.headerProblems(),
            "The study form in C1 or the admission year of the first session was not read"
        );
    }

    @Test
    void cannotTakeYearOfTransferForAdmissionYear() throws Exception {
        final StatementImport statement = read(Books.statement(
            student("Андреев А. А.", "Очная", 2, "06.02.2023 - 27.08.2023", row("Физика", 108, null, 4, null))
        ));
        assertEquals(
            Integer.valueOf(2021), statement.admissionYear(),
            "A student who came in the second course in 2023 was not counted as admitted in 2021"
        );
    }

    @Test
    void cannotHideSheetOfAnotherForm() throws Exception {
        final StatementImport statement = read(Books.statement(
            student("Андреев А. А.", "Очная", 1, Books.SESSION, row("Математика", 108, null, 4, null)),
            student("Борисов Б. Б.", "Очная", 1, Books.SESSION, row("Математика", 108, null, 5, null)),
            student("Васильев В. В.", "Заочная", 1, Books.SESSION, row("Математика", 108, null, 5, null))
        ));
        assertTrue(
            statement.form().equals("очная") && statement.headerProblems().size() == 1
                && statement.headerProblems().get(0).contains("Васильев В. В."),
            "A sheet of another study form than the group was not reported: " + statement.headerProblems()
        );
    }

    @Test
    void cannotLoseStudentNumber() throws Exception {
        final StatementImport statement = read(Books.statement(TWO_SEMESTERS));
        assertEquals(
            "3210301000", statement.sheet("Тестов Т. Т.").number(),
            "The student number of H1 was not read, or kept its «№»"
        );
    }
}
