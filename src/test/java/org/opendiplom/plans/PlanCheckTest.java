package org.opendiplom.plans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opendiplom.Plans;

/** The sums a plan keeps in its rows, parts, blocks and total. */
final class PlanCheckTest {
    private static List<String> findings(final List<PlanRow> rows) {
        return PlanCheck.of(PlanStructure.of(rows)).findings().stream().map(PlanCheck.Finding::toString)
            .collect(Collectors.toList());
    }

    @Test
    void cannotFaultPlanWhoseSumsHold() {
        assertEquals(
            Collections.emptyList(), findings(Plans.bachelor()),
            "A plan whose sums hold, physical culture electives outside their part, got findings"
        );
    }

    @Test
    void cannotMissElementThatBreaksItsPart() {
        final List<String> found = findings(
            Plans.with(Plans.bachelor(), "Физика", Plans.row("Б.1.1.2", "Физика", ";2;;;", "5 - 5", "180 - 180 50 130"))
        );
        assertTrue(
            found.contains("ERROR Б.1.1 «Обязательная часть»: з.е. 12, а сумма вложенных строк 13"),
            "Credits of an element that do not add up to its part were not found: " + found
        );
        assertTrue(
            found.stream().allMatch(finding -> finding.startsWith("ERROR Б.1.1 «")),
            "A wrong element showed anywhere but at the row above it: " + found
        );
    }

    @Test
    void cannotMissRowWhoseHoursDisagreeWithCredits() {
        final List<String> found = findings(
            Plans.with(Plans.bachelor(), "Физика", Plans.row("Б.1.1.2", "Физика", ";2;;;", "4 - 4", "150 - 150 50 100"))
        );
        assertTrue(
            found.contains("ERROR Б.1.1.2 «Физика»: часов 150, а 4 з.е. × 36 = 144"),
            "Hours that are not 36 per credit were not found: " + found
        );
    }

    @Test
    void cannotMissClassesThatDisagreeWithContactAndSelfStudy() {
        final List<String> found = findings(
            Plans.with(Plans.bachelor(), "Физика", Plans.row("Б.1.1.2", "Физика", ";2;;;", "4 - 4", "144 - 144 50 90"))
        );
        assertTrue(
            found.contains("ERROR Б.1.1.2 «Физика»: часов на занятия 144, а контактной и самостоятельной вместе 140"),
            "Hours for classes other than contact and self-study were not found: " + found
        );
    }

    @Test
    void cannotMissTotalOtherThanBlocks() {
        final List<String> found = findings(Plans.with(
            Plans.bachelor(), "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ",
            Plans.row("", "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", "", "22 1 21", "792 36 756 160 596")
        ));
        assertTrue(
            found.contains("ERROR «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»: з.е. 22, а сумма блоков 1–3 21"),
            "A program total other than the sum of blocks 1–3 was not found: " + found
        );
    }

    @Test
    void cannotPassPlanWithoutBreakdownSilently() {
        final List<PlanRow> rows = new ArrayList<>();
        for (final PlanRow row : Plans.bachelor()) {
            rows.add(new PlanRow(
                row.index(), row.name(), row.controls(), Collections.singletonList(row.credits()),
                Collections.emptyList()
            ));
        }
        final List<String> found = findings(rows);
        assertTrue(
            found.stream().anyMatch(finding -> finding.startsWith("WARNING В плане нет разбивки")),
            "A plan with only the total credits was not reported as checked in part: " + found
        );
    }

    @Test
    void cannotPassPlanWithoutTotalSilently() {
        final List<PlanRow> rows = Plans.bachelor();
        rows.remove(rows.size() - 1);
        assertEquals(
            Collections.singletonList(
                "WARNING Нет строки «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»: объём программы не проверен"
            ),
            findings(rows),
            "A plan without its total was not reported"
        );
    }

    @Test
    void cannotPassEmptyElementSilently() {
        final List<String> found = findings(Plans.with(
            Plans.bachelor(), "Математика", Plans.row("Б.1.1.1", "Математика", "", "- - -", "- - - - -")
        ));
        assertTrue(
            found.stream().anyMatch(finding -> finding.startsWith("WARNING Б.1.1.1 «Математика»: нет ни з.е., ни часов")),
            "An element with no credits and no hours was not reported: " + found
        );
    }
}
