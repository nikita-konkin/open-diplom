package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The expressions of the CyberDiploma templates. */
final class ExpressionsTest {
    private final Set<String> problems = new LinkedHashSet<>();

    private String text(final String template) {
        final Map<String, Object> fields = new HashMap<>();
        fields.put("Документ.\"Дата выдачи\"", "2026-07-03");
        fields.put("Документ.\"Диплом: С отличием\"", 1L);
        fields.put("Документ.\"Приложение: Дубликат\"", 0L);
        fields.put("Студент.\"Дата решения госкомиссии\"", "2026-06-04");
        fields.put("Студент.\"Председатель: Имя\"", "Сергей");
        fields.put("Образовательное учреждение.\"Наименование\"", "университет");
        return new Expressions(fields, this.problems).text(template);
    }

    @Test
    void cannotPrintDateOfIssue() {
        assertEquals("3 июля 2026 года", this.text("[DateInGenitive(<Документ.\"Дата выдачи\">)] года"),
            "The date of issue was not printed as the 2026 supplements print it, without a leading zero");
    }

    @Test
    void cannotSplitDateOfCommission() {
        assertEquals("«04» июня 2026 г. — 26",
            this.text("«[Студент.\"Дата решения госкомиссии\" #Ddd]» [MonthInGenitive(<Студент.\"Дата решения госкомиссии\">)] "
                + "[YearOf(<Студент.\"Дата решения госкомиссии\">)] г. — "
                + "[Copy(IntToStr(YearOf(<Студент.\"Дата решения госкомиссии\">)),3,2)]"),
            "The day, month and year of the decision of the commission were not printed as the diploma asks");
    }

    @Test
    void cannotChooseHonorsWrong() {
        assertEquals("бакалавра с отличием /",
            this.text("бакалавра [IIF(<Документ.\"Диплом: С отличием\">=1,'с отличием','')] /"
                + "[IIF(<Документ.\"Приложение: Дубликат\">=1,'ДУБЛИКАТ','')]"),
            "«с отличием» or «ДУБЛИКАТ» was printed against the data");
    }

    @Test
    void cannotPrintInitialWrong() {
        assertEquals("С.", this.text("[Copy(<Студент.\"Председатель: Имя\">,1,1)]."), "The initial was not the first letter");
    }

    @Test
    void cannotMissFieldWrittenInOtherCase() {
        assertEquals("университет", this.text("[Образовательное учреждение.\"наименование\"]"),
            "A field written in lower case was not found, as FastReport finds it");
        assertTrue(this.problems.isEmpty(), "A field found was reported: " + this.problems);
    }

    @Test
    void cannotHideFieldDataHasNot() {
        assertEquals("Часов: ", this.text("Часов: [Модули и разделы.\"Количество часов\"]"),
            "A field the data has not printed something");
        assertTrue(this.problems.iterator().next().contains("Количество часов"),
            "A field the data has not went without a word: " + this.problems);
    }

    @Test
    void cannotHideUnknownFunction() {
        assertEquals("", this.text("[FormatFloat('0.00', 1)]"), "An unknown function printed something");
        assertTrue(this.problems.iterator().next().contains("FormatFloat"),
            "An unknown function went without a word: " + this.problems);
    }
}
