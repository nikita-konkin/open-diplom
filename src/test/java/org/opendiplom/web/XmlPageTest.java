package org.opendiplom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.opendiplom.Books;
import org.opendiplom.imports.Curriculum;

/** Fields of the XML form taken from a curriculum. */
final class XmlPageTest {
    private static String field(final String direction, final String form, final String term, final String name)
        throws Exception {
        final Curriculum plan = Curriculum.read(Books.book(
            new Object[] {"", "НАПРАВЛЕНИЕ ПОДГОТОВКИ  " + direction},
            new Object[] {"", "Профиль: (11) \"Интеллектуальные телекоммуникационные системы и сети\"",
                "Форма обучения - " + form},
            new Object[] {"", "", "Срок получения образования - " + term},
            new Object[] {"№", "Структура ОП", "Кафедра", "Объем частей ОП\nв зачетных единицах"},
            new Object[] {"", "", "", "Всего"},
            new Object[] {"Б.1.1.1", "Математика", "Кафедра", 16}
        ));
        return XmlPage.fromPlan(plan, new HashMap<>()).get(name);
    }

    @Test
    void cannotPrintPartTimeTermOfBachelorPlan() throws Exception {
        assertEquals(
            "4 года", field("11.03.02  ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ", "Заочная", "4 года 6 месяцев",
                "study_term"),
            "A part-time bachelor got the term of the plan, not the normative full-time one (B-35)"
        );
    }

    @Test
    void cannotPrintPartTimeTermOfMasterPlan() throws Exception {
        assertEquals(
            "2 года", field("11.04.02  ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ", "Заочная", "2 года 6 месяцев",
                "study_term"),
            "A part-time master got the term of the plan, not the normative full-time one (B-35)"
        );
    }

    @Test
    void cannotGuessTermOfLevelWithoutRule() throws Exception {
        assertEquals(
            "5 лет 6 месяцев", field("10.05.02  ИНФОРМАЦИОННАЯ БЕЗОПАСНОСТЬ ТЕЛЕКОММУНИКАЦИОННЫХ СИСТЕМ", "Заочная",
                "5 лет 6 месяцев", "study_term"),
            "A level without a decided rule got an invented term instead of the plan's"
        );
    }

    @Test
    void cannotPrintDirectionInCapitals() throws Exception {
        assertEquals(
            "11.03.02 Инфокоммуникационные технологии и системы связи",
            field("11.03.02  ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ", "Очная", "4 года", "direction"),
            "The direction is not written as supplements print it"
        );
    }
}
