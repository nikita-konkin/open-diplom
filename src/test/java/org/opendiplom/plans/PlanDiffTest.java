package org.opendiplom.plans;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opendiplom.Plans;

/** What changed between two plans of a program. */
final class PlanDiffTest {
    private static List<String> changes(final List<PlanRow> before, final List<PlanRow> after) {
        return PlanDiff.of(PlanStructure.of(before), PlanStructure.of(after)).changes().stream()
            .map(PlanDiff.Change::toString).collect(Collectors.toList());
    }

    @Test
    void cannotFindChangesBetweenSamePlans() {
        assertEquals(Collections.emptyList(), changes(Plans.bachelor(), Plans.bachelor()), "Equal plans differed");
    }

    @Test
    void cannotTakeRenumberedElementForAnother() {
        assertEquals(
            Collections.emptyList(),
            changes(Plans.bachelor(), Plans.with(
                Plans.bachelor(), "Физика", Plans.row("Б.1.1.7", "Физика", ";2;;;", "4 - 4", "144 - 144 50 94")
            )),
            "An element with a new index was taken for a new element"
        );
    }

    @Test
    void cannotLoseCreditsAndControlOfPractice() {
        assertEquals(
            Collections.singletonList(
                "CHANGED Производственная практика (преддипломная практика) [з.е.: 6 → 9, зачеты с оценкой: 8 → 6,8]"
            ),
            changes(Plans.bachelor(), Plans.with(
                Plans.bachelor(), Plans.PRACTICE, Plans.row("Б.2.1.1.1", Plans.PRACTICE, ";;6,8;;", "9 - 9", "")
            )),
            "A practice given more credits and another semester was not reported by its printed name"
        );
    }

    @Test
    void cannotLoseAddedAndRemovedElements() {
        final List<String> found = changes(Plans.bachelor(), Plans.with(
            Plans.bachelor(), "Физика", Plans.row("Б.1.1.2", "Химия", "2;;;;", "4 - 4", "144 - 144 50 94")
        ));
        assertEquals(
            List.of("ADDED Химия [з.е.: 4, экзамены: 2]", "REMOVED Физика [з.е.: 4, зачеты: 2]"), found,
            "An element replaced by another was not reported as removed and added"
        );
    }

    @Test
    void cannotLoseChangeOfTotals() {
        final List<String> found = changes(Plans.bachelor(), Plans.with(
            Plans.bachelor(), "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ",
            Plans.row("", "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ", "", "21 1 20", "756 36 720 60 660")
        ));
        assertTrue(
            found.contains("TOTALS  [контактная работа, ч: 160 → 60]"),
            "A change of the contact hours of the program was not reported: " + found
        );
    }
}
