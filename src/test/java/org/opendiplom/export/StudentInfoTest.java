package org.opendiplom.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.sheets.Workbooks;

/** The student information file as the graduations read it. */
final class StudentInfoTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private static StudentInfo read(final List<String> extra, final Object[]... rows) throws Exception {
        return StudentInfo.read(Workbooks.read(Books.info(extra, rows), "Сведения").get(0), TODAY);
    }

    @Test
    void cannotLoseStateExamGrade() throws Exception {
        final StudentInfo info = read(
            Collections.singletonList(StudentInfo.STATE_EXAM), Books.graduate("Иванов Иван Иванович", 4.0)
        );
        assertEquals(
            "true 4", info.stateExams() + " " + info.entries().get(0).stateExamGrade,
            "The grade of the state exam from its own column was not read"
        );
    }

    @Test
    void cannotAcceptStateExamGradeThatIsNotCode() throws Exception {
        final StudentInfo info = read(
            Collections.singletonList(StudentInfo.STATE_EXAM), Books.graduate("Иванов Иван Иванович", "отл")
        );
        assertTrue(
            info.problems().size() == 1 && info.problems().get(0).contains("ОценкаГосэкзамен «отл»"),
            "A state exam grade that is no code 2–7 was accepted: " + info.problems()
        );
    }

    @Test
    void cannotDropGraduateWithUnreadDate() throws Exception {
        final Object[] row = Books.graduate("Иванов Иван Иванович");
        row[1] = "3 февраля 2001";
        final StudentInfo info = read(Collections.emptyList(), row);
        assertEquals(
            Arrays.asList("Иванов", "", "строка 2 файла сведений"),
            Arrays.asList(
                info.entries().get(0).lastName, info.entries().get(0).birthDate,
                info.entries().get(0).problems.get(0).replaceAll(".*\\((строка 2 файла сведений)\\).*", "$1")
            ),
            "A graduate with a date that could not be read was dropped, or the problem does not name the row"
        );
    }

    @Test
    void cannotMatchByFirstNameInsteadOfInitials() throws Exception {
        final StudentInfo info = read(Collections.emptyList(), Books.graduate("Иванов Иван Петрович"));
        assertEquals(
            Arrays.asList("иванов", "и", "п"), info.entries().get(0).key(),
            "The key of a graduate is not the surname and the initials a statement has"
        );
    }
}
