package org.opendiplom.plans;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opendiplom.Plans;
import org.opendiplom.plans.PlanItem.Section;

/** Parents, sections, leaves and printed names of a plan. */
final class PlanStructureTest {
    private static PlanStructure plan() {
        return PlanStructure.of(Plans.bachelor());
    }

    private static PlanItem item(final PlanStructure plan, final String name) {
        return plan.items().stream().filter(item -> item.row().name().startsWith(name)).findFirst()
            .orElseThrow(() -> new AssertionError("No row «" + name + "»"));
    }

    private static String parent(final PlanStructure plan, final String name) {
        final int parent = item(plan, name).parent();
        return parent < 0 ? "—" : plan.items().get(parent).row().name();
    }

    @Test
    void cannotLoseParentsOfNestedRows() {
        final PlanStructure plan = plan();
        assertEquals(
            "Обязательная часть / Блок 1. Дисциплины (модули) / —",
            String.join(" / ", parent(plan, "Математика"), parent(plan, "Обязательная часть"),
                parent(plan, "Блок 1")),
            "An element did not go under its part, or the part under its block"
        );
    }

    @Test
    void cannotNestRowUnderRowOfSameIndex() {
        final PlanStructure plan = plan();
        assertEquals(
            "Обязательная часть / Обязательная часть",
            parent(plan, "Выполнение и защита") + " / " + parent(plan, "Подготовка к сдаче"),
            "Of two rows indexed «Б.3.1.1» the second went under the first"
        );
    }

    @Test
    void cannotTakeFacultativeForDiscipline() {
        final PlanStructure plan = plan();
        assertEquals(
            Section.FACULTATIVES + " / Факультативные дисциплины",
            item(plan, "Теория игр").section() + " / " + parent(plan, "Теория игр"),
            "A facultative after its heading was taken for a discipline of block 1"
        );
    }

    @Test
    void cannotMixSectionsOfBlocks() {
        final PlanStructure plan = plan();
        assertEquals(
            "DISCIPLINES PRACTICES ATTESTATION NONE",
            Arrays.asList("Физика", Plans.PRACTICE, "Выполнение и защита", "ОБЪЕМ").stream()
                .map(name -> item(plan, name).section().name()).collect(Collectors.joining(" ")),
            "A row got the section of another block"
        );
    }

    @Test
    void cannotTakeGroupForLeaf() {
        assertEquals(
            Arrays.asList("Б.1.1.1", "Б.1.1.2", "Б.1.1.3", "1", "Б.2.1.1.1", "Б.3.1.1", "Б.3.1.1"),
            plan().leaves().stream().map(item -> item.row().index()).collect(Collectors.toList()),
            "A part, a block or the kind of a practice was taken for what a graduate gets a grade for"
        );
    }

    @Test
    void cannotPrintPracticeWithoutItsKind() {
        assertEquals(
            "Производственная практика (преддипломная практика)", item(plan(), Plans.PRACTICE).printed(),
            "A practice was not printed as «Вид (тип)»"
        );
    }

    @Test
    void cannotRepeatKindAlreadyInTypeOfPractice() {
        assertEquals(
            "Учебная практика (ознакомительная)",
            PlanStructure.practice("Учебная практика", "Учебная практика (ознакомительная)"),
            "The kind of a practice was repeated though its type already names it"
        );
    }

    @Test
    void cannotLoseAlternativesWithBracketsInside() {
        assertEquals(
            Arrays.asList("Основы сетей (вводный курс)", "Сенсорные сети"),
            PlanStructure.alternatives("Элективная дисциплина 1 (Основы сетей (вводный курс) / Сенсорные сети)"),
            "Alternatives of an elective were split inside a bracket or lost"
        );
    }

    @Test
    void cannotInventAlternativesOfPlainBrackets() {
        final List<String> none = Collections.emptyList();
        assertEquals(
            none, PlanStructure.alternatives("История (история России, всеобщая история)"),
            "Brackets without « / » were taken for a choice"
        );
    }
}
