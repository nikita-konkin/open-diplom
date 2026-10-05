package org.opendiplom.printing;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Organization;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.export.Program;

/** The data sets of CyberDiploma from the registry. */
final class BlankDataTest {
    private static Program program() throws Exception {
        return new Program.Builder().direction("11.03.02 Инфокоммуникационные технологии и системы связи")
            .profile("Интеллектуальные сети").qualification("бакалавр").studyForm("очная").studyTerm("4 года")
            .programCredits("240").contactHours("3248 ак.час").practiceCredits("24").finalCredits("9")
            .gekChairman("Иванов И. И.").build();
    }

    private static GraduateRecord graduate() {
        return new GraduateRecord(
            "g", 0, "Андреев", "Андрей", "Андреевич", "2004-02-03", "Аттестат о среднем общем образовании", "2021",
            "2026-06-24", "3", "Сеть связи", 5, 4, "Андреев А. А.", "3210301000", "", Arrays.asList(
                new ResultRecord(0, ResultRecord.DISCIPLINE, "Математика", 5, "5", 8.0),
                new ResultRecord(1, ResultRecord.COURSE_WORK, "Математика (курсовая работа)", 4, "4", null),
                new ResultRecord(2, ResultRecord.PRACTICE, "Учебная практика (ознакомительная практика)", 6, "V", 3.0),
                new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", 3, "3", 4.5),
                new ResultRecord(4, ResultRecord.FACULTATIVE, "Теория игр", 6, "V", 2.0),
                new ResultRecord(5, ResultRecord.FACULTATIVE, "Логика", null, "", 2.0)
            )
        );
    }

    private static BlankData data(final boolean apart) throws Exception {
        return BlankData.of(program(), graduate(),
            new DocumentRecord("d", "g", null, false, false, "10001", "2026-07-03", null), true,
            new Organization("университет", "г. Йошкар-Ола", "Петров", "Пётр", "Петрович"), true, apart);
    }

    private static List<String> rows(final BlankData data, final String dataset) {
        final List<String> rows = new ArrayList<>();
        for (final Map<String, Object> row : data.rows(dataset)) {
            rows.add(String.join(" | ", row.values().toArray(new String[0])));
        }
        return rows;
    }

    @Test
    void cannotOrderTableOtherwiseThanCyberDiploma() throws Exception {
        assertEquals(Arrays.asList(
            "Математика | 8 з.е. | отлично",
            "Физика | 4,5 з.е. | удовлетворительно",
            "Практики | 24 з.е. | x",
            "в том числе: |  | ",
            "Учебная практика (ознакомительная практика) | 3 з.е. | зачтено",
            "Государственная итоговая аттестация | 9 з.е. | x",
            "в том числе: |  | ",
            "Государственный экзамен | х | хорошо",
            "Выпускная квалификационная работа \"Сеть связи\" | х | отлично",
            "Объем образовательной программы | 240 з.е. | x",
            "в том числе объем контактной работы обучающихся во взаимодействии с преподавателем в академических часах: "
                + "| 3248 ак.час | x",
            "Математика (курсовая работа) |  | хорошо",
            "Факультативные дисциплины |  | ",
            "в том числе: |  | ",
            "Теория игр | 2 з.е. | зачтено"
        ), rows(data(false), BlankData.MODULES), "The rows of the table are not those of the 2026 supplements");
    }

    @Test
    void cannotPrintCourseWorksTwice() throws Exception {
        final BlankData data = data(true);
        assertTrue(rows(data, BlankData.MODULES).stream().noneMatch(row -> row.contains("курсовая")),
            "A course work printed in the table though the template has a band of its own for them");
        assertEquals(Arrays.asList("Математика (курсовая работа) | хорошо"), rows(data, BlankData.COURSE_WORKS),
            "The course works were not in their data set");
    }

    @Test
    void cannotMissFieldOfDocument() throws Exception {
        final Map<String, Object> fields = data(false).fields();
        assertEquals("10001", fields.get("Документ.\"Регистрационный номер\""), "The registration number was lost");
        assertEquals(1L, fields.get("Документ.\"Диплом: С отличием\""), "«с отличием» decided was lost");
        assertEquals("11.03.02", fields.get("Студент.\"Специальность: Код\""), "The code of the direction was lost");
        assertEquals("Инфокоммуникационные технологии и системы связи", fields.get("Студент.\"Специальность: Наименование\""),
            "The direction was printed otherwise than as a sentence");
        assertEquals("И", fields.get("Студент.\"Председатель: Имя\""), "The initial of the chairman was lost");
        assertEquals(Arrays.asList(
            "Направленность (профиль) образовательной программы: \"Интеллектуальные сети\"", "Форма обучения: очная"
        ), rows(data(false), BlankData.EXTRA), "The additional information is not that of the XML");
    }

    @Test
    void cannotSplitChairmanWrong() {
        assertArrayEquals(new String[] {"Иванов", "Иван", "Иванович"}, BlankData.person("Иванов Иван Иванович"),
            "A full name was split wrong");
        assertArrayEquals(new String[] {"Иванов", "И", "И"}, BlankData.person("Иванов И.И."),
            "Initials after the surname were split wrong");
        assertArrayEquals(new String[] {"Иванов", "И", "П"}, BlankData.person("И. П. Иванов"),
            "Initials before the surname were split wrong");
    }
}
