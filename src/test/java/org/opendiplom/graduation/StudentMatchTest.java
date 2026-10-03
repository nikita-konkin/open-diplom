package org.opendiplom.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.export.StudentInfo;
import org.opendiplom.sheets.Workbooks;

/** Rows of the information file against sheets of the statement (ADR-0004). */
final class StudentMatchTest {
    private static List<StudentInfo.Entry> entries(final String... names) throws Exception {
        final List<Object[]> rows = new ArrayList<>();
        for (final String name : names) {
            rows.add(Books.graduate(name));
        }
        return StudentInfo.read(
            Workbooks.read(Books.info(Collections.emptyList(), rows.toArray(new Object[0][])), "Сведения").get(0),
            LocalDate.of(2026, 9, 26)
        ).entries();
    }

    private static String statuses(final StudentMatch match) {
        final List<String> statuses = new ArrayList<>();
        for (final StudentMatch.Pair pair : match.pairs()) {
            statuses.add(pair.status + (pair.student == null ? "" : " " + pair.student));
        }
        return String.join(", ", statuses);
    }

    @Test
    void cannotSwapNamesakes() throws Exception {
        final StudentMatch match = StudentMatch.of(
            entries("Иванов Пётр Иванович", "Иванов Иван Иванович"), Arrays.asList("Иванов И. И.", "Иванов П. И."),
            Map.of()
        );
        assertEquals(
            "MATCHED Иванов П. И., MATCHED Иванов И. И.", statuses(match),
            "Namesakes with different initials got each other's sheets"
        );
    }

    @Test
    void cannotGuessBetweenTwoFittingSheets() throws Exception {
        final StudentMatch match = StudentMatch.of(
            entries("Иванов Иван"), Arrays.asList("Иванов И. И.", "Иванов И. П."), Map.of()
        );
        assertEquals(
            "AMBIGUOUS, NO_INFO Иванов И. И., NO_INFO Иванов И. П.", statuses(match),
            "A graduate without a patronymic was given one of two sheets that fit"
        );
    }

    @Test
    void cannotGiveOneSheetToTwoGraduates() throws Exception {
        final StudentMatch match = StudentMatch.of(
            entries("Иванов Иван", "Иванов Игорь"), Collections.singletonList("Иванов И. И."), Map.of()
        );
        assertFalse(
            match.resolved() || statuses(match).contains("MATCHED"),
            "Two graduates shared one sheet: " + statuses(match)
        );
    }

    @Test
    void cannotDropGraduateOrSheetSilently() throws Exception {
        final StudentMatch match = StudentMatch.of(
            entries("Петров Пётр Петрович"), Collections.singletonList("Сидоров С. С."), Map.of()
        );
        assertEquals(
            "NO_STATEMENT, NO_INFO Сидоров С. С. false", statuses(match) + " " + match.resolved(),
            "A graduate without a sheet or a sheet without a graduate was not reported"
        );
    }

    @Test
    void cannotIgnoreOperatorsChoices() throws Exception {
        final List<String> sheets = Arrays.asList("Иванов И. И.", "Иванов И. П.");
        final String row = StudentMatch.of(entries("Иванов Иван"), sheets, Map.of()).pairs().get(0).item();
        final StudentMatch match = StudentMatch.of(
            entries("Иванов Иван"), sheets,
            Map.of(row, "Иванов И. П.", StudentMatch.SHEET + "Иванов И. И.", StudentMatch.EXCLUDED)
        );
        assertTrue(
            match.resolved() && "CHOSEN Иванов И. П., EXCLUDED Иванов И. И.".equals(statuses(match)),
            "The sheet chosen and the sheet left out by the operator were not taken: " + statuses(match)
        );
    }

    @Test
    void cannotLoseChoiceWhenRowsMove() throws Exception {
        final List<String> sheets = Arrays.asList("Иванов И. И.", "Иванов И. П.", "Петров П. П.");
        final String row = StudentMatch.of(entries("Иванов Иван", "Петров Пётр Петрович"), sheets, Map.of())
            .pairs().get(0).item();
        final StudentMatch match = StudentMatch.of(
            entries("Петров Пётр Петрович", "Иванов Иван"), sheets,
            Map.of(row, "Иванов И. П.", StudentMatch.SHEET + "Иванов И. И.", StudentMatch.EXCLUDED)
        );
        assertEquals(
            "MATCHED Петров П. П., CHOSEN Иванов И. П., EXCLUDED Иванов И. И.", statuses(match),
            "The operator's choice was lost or went to another graduate when the rows of the file moved"
        );
    }

    @Test
    void cannotGiveChoiceToNamesakeRow() throws Exception {
        final List<String> sheets = Arrays.asList("Иванов И. И.", "Иванов И. П.");
        final List<StudentInfo.Entry> rows = entries("Иванов Иван", "Иванов Иван");
        final String first = StudentMatch.of(rows, sheets, Map.of()).pairs().get(0).item();
        final StudentMatch match = StudentMatch.of(rows, sheets, Map.of(first, "Иванов И. П."));
        assertEquals(
            "CHOSEN Иванов И. П., AMBIGUOUS, NO_INFO Иванов И. И.", statuses(match),
            "The choice about one of two rows with the same name went to both"
        );
    }

    @Test
    void cannotMissLatinLookalikeInName() throws Exception {
        final StudentMatch match = StudentMatch.of(
            entries("Ивaнов Иван Иванович"), Collections.singletonList("Иванов И. И."), Map.of()
        );
        assertTrue(match.resolved(), "A Latin «a» in the surname kept the graduate from the sheet: " + statuses(match));
    }
}
