package org.opendiplom.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.opendiplom.sheets.Sheet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Ported from the Python service (test_xml_generator.py), same failure scenarios. */
final class CyberDiplomaXmlTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    private static Program.Builder program() {
        return new Program.Builder()
            .studyTerm("4 года")
            .qualification("бакалавр")
            .studyForm("очная")
            .direction("09.03.02 ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ")
            .profile("Интеллектуальные информационные системы и технологии")
            .programCredits(240)
            .contactHours("3180 ак.час")
            .practiceCredits(12)
            .finalCredits(9)
            .gekChairman("Председатель");
    }

    private static Map<String, Object> student() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("ФИО", "Иванов Иван Иванович");
        row.put("ДатаРожд", LocalDateTime.of(2001, 2, 3, 0, 0));
        row.put("НаименованиеДокПредОбр", "Аттестат о среднем общем образовании");
        row.put("ГодДокПредОбр", 2019.0);
        row.put("ТемаВКР", "Тема");
        row.put("НомерПротоколаГэк", 3.0);
        row.put("ДатаРешенияГэк", LocalDateTime.of(2026, 6, 24, 0, 0));
        row.put("ОценкаВКР", 5.0);
        return row;
    }

    private static Map<String, Object> student(final String field, final Object value) {
        final Map<String, Object> row = student();
        row.put(field, value);
        return row;
    }

    @SafeVarargs
    private static Sheet students(final Map<String, Object>... rows) {
        final List<List<Object>> lines = new ArrayList<>();
        lines.add(new ArrayList<>(student().keySet()));
        for (final Map<String, Object> row : rows) {
            lines.add(new ArrayList<>(row.values()));
        }
        return new Sheet("Лист1", lines);
    }

    /** Pivot: a row per label, a column per student header. */
    private static Sheet pivot(final List<String> labels, final Object[]... columns) {
        final List<List<Object>> lines = new ArrayList<>();
        final List<Object> header = new ArrayList<>(Arrays.asList("Дисциплины"));
        for (final Object[] column : columns) {
            header.add(column[0]);
        }
        lines.add(header);
        for (int row = 0; row < labels.size(); ++row) {
            final List<Object> line = new ArrayList<>(Arrays.asList(labels.get(row)));
            for (final Object[] column : columns) {
                line.add(column[row + 1]);
            }
            lines.add(line);
        }
        return new Sheet("Report", lines);
    }

    private static Sheet pivot(final Object... column) {
        return pivot(Arrays.asList("Математика_дисциплина_3"), column);
    }

    private static Sheet pivot() {
        return pivot(new Object[] {"Иванов И. И.", 5.0});
    }

    private static String xml(final Program.Builder builder, final Sheet grades, final Sheet info)
        throws Exception {
        return CyberDiplomaXml.write(builder.build(), PivotSource.read(grades, info, TODAY));
    }

    private static String xml(final Sheet grades, final Sheet info) throws Exception {
        return xml(program(), grades, info);
    }

    private static String problems(final Executable call) {
        return assertThrows(ValidationProblems.class, call).getMessage();
    }

    private static Document document(final String xml) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String first(final String xml, final String tag) throws Exception {
        final NodeList nodes = document(xml).getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String firstIn(final String xml, final String parent, final String tag) throws Exception {
        final Element element = (Element) document(xml).getElementsByTagName(parent).item(0);
        return element.getElementsByTagName(tag).item(0).getTextContent();
    }

    private static Sheet statePivot(final Object grade) {
        return pivot(
            Arrays.asList(
                "Математика_дисциплина_3",
                "Выполнение и защита выпускной квалификационной работы",
                "Подготовка к сдаче и сдача государственного экзамена"
            ),
            new Object[] {"Иванов И. И.", 5.0, 7.0, grade}
        );
    }

    @Test
    void cannotPutTimeIntoBirthDate() throws Exception {
        assertEquals(
            "2001-02-03",
            first(xml(pivot(), students(student("ДатаРожд", LocalDateTime.of(2001, 2, 3, 14, 25, 59)))), "ДатаРожд"),
            "ДатаРожд contains a time component"
        );
    }

    @Test
    void cannotAcceptInvalidBirthDate() {
        assertTrue(
            problems(() -> xml(pivot(), students(student("ДатаРожд", "not-a-date"))))
                .contains("ДатаРожд: не удалось распознать дату"),
            "Invalid ДатаРожд was accepted"
        );
    }

    @Test
    void cannotAcceptBirthDateTypedAsNumber() {
        assertTrue(
            problems(() -> xml(pivot(), students(student("ДатаРожд", 36925.0)))).contains("дата записана числом"),
            "An Excel serial number was silently read as a date"
        );
    }

    @Test
    void cannotDropStudentMissingFromPivot() {
        assertTrue(
            problems(() -> xml(pivot(), students(student(), student("ФИО", "Петров Пётр Петрович"))))
                .contains("Петров Пётр Петрович (строка 3 файла сведений): нет колонки"),
            "A student without grades was silently left out of the XML"
        );
    }

    @Test
    void cannotGiveNamesakeGradesOfAnotherStudent() throws Exception {
        assertEquals(
            "3",
            firstIn(xml(
                pivot(Arrays.asList("Математика_дисциплина_3"),
                    new Object[] {"Петров А. И.", 5.0}, new Object[] {"Петров Б. В.", 3.0}),
                students(student("ФИО", "Петров Борис Викторович"))
            ), "Дисциплина", "Оценка"),
            "A student received the grades of a namesake"
        );
    }

    @Test
    void cannotPickOneOfTwoColumnsForSurnameOnlyMatch() {
        assertTrue(
            problems(() -> xml(
                pivot(Arrays.asList("Математика_дисциплина_3"),
                    new Object[] {"Петров", 5.0}, new Object[] {"Петров А.", 3.0}),
                students(student("ФИО", "Петров Алексей Иванович"))
            )).contains("подходит несколько колонок"),
            "An ambiguous pivot column was chosen silently"
        );
    }

    @Test
    void cannotMissStudentWhenPivotWritesYoAsYe() throws Exception {
        assertEquals(
            "Ёжиков",
            first(xml(pivot(new Object[] {"Ежиков А. Б.", 4.0}), students(student("ФИО", "Ёжиков Алексей Борисович"))),
                "Фамилия"),
            "Ё/Е spelling difference dropped the student"
        );
    }

    @Test
    void cannotAcceptLatinLetterInsideRussianSurname() {
        assertTrue(
            problems(() -> xml(
                pivot(new Object[] {"Ёжиков А. Б.", 4.0}), students(student("ФИО", "Ëжиков Алексей Борисович"))
            )).contains("смешаны латинские и русские буквы"),
            "A Latin Ë in a surname would be printed on the diploma"
        );
    }

    @Test
    void cannotWriteNanForMissingPatronymic() throws Exception {
        assertEquals(
            "",
            first(xml(pivot(new Object[] {"Ли В.", 5.0}), students(student("ФИО", "Ли Вэй"))), "Отчество"),
            "A missing patronymic was written as text"
        );
    }

    @Test
    void cannotAcceptEmptyProtocolNumber() {
        assertTrue(
            problems(() -> xml(pivot(), students(student("НомерПротоколаГэк", null))))
                .contains("не заполнено поле НомерПротоколаГэк"),
            "An empty ГЭК protocol number reached the XML"
        );
    }

    @Test
    void cannotAcceptThreeDigitYearOfPreviousDocument() {
        assertTrue(
            problems(() -> xml(pivot(), students(student("ГодДокПредОбр", 209.0))))
                .contains("ГодДокПредОбр должен быть годом из четырёх цифр"),
            "A three-digit year of the previous document was accepted"
        );
    }

    @Test
    void cannotWriteProtocolNumberAsFloat() throws Exception {
        assertEquals(
            "3", first(xml(pivot(), students(student("НомерПротоколаГэк", 3.0))), "НомерПротоколаГэк"),
            "The protocol number kept a float suffix"
        );
    }

    @Test
    void cannotSkipQuestionMarkInsteadOfGrade() {
        assertTrue(
            problems(() -> xml(pivot(new Object[] {"Иванов И. И.", "?"}), students(student())))
                .contains("«Математика» — недопустимая оценка «?»"),
            "A discipline marked with '?' was silently left out"
        );
    }

    @Test
    void cannotAcceptGradeCodeOutsideCyberDiplomaScale() {
        assertTrue(
            problems(() -> xml(pivot(new Object[] {"Иванов И. И.", 8.0}), students(student())))
                .contains("недопустимая оценка «8»"),
            "Grade code 8 is not a CyberDiploma grade but was accepted"
        );
    }

    @Test
    void cannotRejectNotDonePlaceholderGrade() throws Exception {
        assertEquals(
            "7", firstIn(xml(pivot(new Object[] {"Иванов И. И.", 7.0}), students(student())), "Дисциплина", "Оценка"),
            "Placeholder grade 7 («не выполнял») was rejected"
        );
    }

    @Test
    void cannotDropGradedRowWithoutTypeSuffix() {
        assertTrue(
            problems(() -> xml(
                pivot(Arrays.asList("Математика_дисциплина_3", "Физические основы электротехники"),
                    new Object[] {"Иванов И. И.", 5.0, 4.0}),
                students(student())
            )).contains("«Физические основы электротехники» содержит оценки"),
            "A graded discipline without a type suffix was silently dropped"
        );
    }

    @Test
    void cannotTreatFinalAttestationRowsAsBrokenDisciplines() throws Exception {
        assertEquals(
            1,
            document(xml(
                pivot(Arrays.asList(
                    "Математика_дисциплина_3",
                    "Выполнение и защита выпускной квалификационной работы",
                    "Подготовка к сдаче и сдача государственного экзамена"
                ), new Object[] {"Иванов И. И.", 5.0, 7.0, 4.0}),
                students(student())
            )).getElementsByTagName("Дисциплина").getLength(),
            "ГИА rows of the pivot template became disciplines"
        );
    }

    @Test
    void cannotPrintProfileInPlaceOfDirection() throws Exception {
        assertEquals(
            "ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ", first(xml(pivot(), students(student())), "НаименованиеСпец"),
            "The supplement shows something other than the direction name"
        );
    }

    @Test
    void cannotLoseProfileInExtraInformation() throws Exception {
        assertEquals(
            "Направленность (профиль) образовательной программы: "
                + "\"Интеллектуальные информационные системы и технологии\"",
            first(xml(pivot(), students(student())), "ДопСвед"),
            "The profile is missing from the extra information"
        );
    }

    @Test
    void cannotAcceptProfileEqualToDirectionName() {
        assertTrue(
            problems(() -> program().direction("09.03.02 Интеллектуальные информационные системы и технологии").build())
                .contains("Профиль совпадает с наименованием направления"),
            "The profile was accepted as the direction name"
        );
    }

    @Test
    void cannotAcceptDirectionWithoutCode() {
        assertTrue(
            problems(() -> program().direction("ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ").build())
                .contains("нужен код вида 09.03.02"),
            "A direction without its code was accepted"
        );
    }

    @Test
    void cannotAcceptMasterDirectionForBachelor() {
        assertTrue(
            problems(() -> program()
                .direction("11.04.02 ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ")
                .profile("Интеллектуальные телекоммуникационные системы и сети")
                .build()
            ).contains("относится к уровню «магистр»"),
            "A master's direction code was accepted for a bachelor"
        );
    }

    @Test
    void cannotReportOnlyTheFirstProblem() {
        final Map<String, Object> row = student("ГодДокПредОбр", 209.0);
        row.put("НомерПротоколаГэк", null);
        assertTrue(
            problems(() -> xml(pivot(), students(row))).startsWith("Найдено ошибок в исходных данных: 2"),
            "Problems were not reported together"
        );
    }

    @Test
    void cannotRejectYearPlaceholder() throws Exception {
        assertEquals(
            "1111", first(xml(pivot(), students(student("ГодДокПредОбр", 1111.0))), "ГодДокПредОбр"),
            "The agreed placeholder year 1111 was rejected"
        );
    }

    @Test
    void cannotLoseStateExamGrade() throws Exception {
        final NodeList exams = document(xml(statePivot(4.0), students(student()))).getElementsByTagName("Госэкзамен");
        String grade = null;
        for (int index = 0; index < exams.getLength(); ++index) {
            final Element exam = (Element) exams.item(index);
            if ("Государственный экзамен".equals(exam.getElementsByTagName("Наименование").item(0).getTextContent())) {
                grade = exam.getElementsByTagName("Оценка").item(0).getTextContent();
            }
        }
        assertEquals("4", grade, "The state exam grade from the pivot did not reach the XML");
    }

    @Test
    void cannotPutStateExamAfterThesis() throws Exception {
        assertEquals(
            "Государственный экзамен", firstIn(xml(statePivot(4.0), students(student())), "Госэкзамен", "Наименование"),
            "The state exam is not listed before the thesis"
        );
    }

    @Test
    void cannotAcceptQuestionMarkForStateExam() {
        assertTrue(
            problems(() -> xml(statePivot("?"), students(student())))
                .contains("«Государственный экзамен» — недопустимая оценка «?»"),
            "A '?' state exam grade was silently dropped"
        );
    }

    @Test
    void cannotAddStateExamWhenRowIsEmpty() throws Exception {
        assertEquals(
            1, document(xml(statePivot(null), students(student()))).getElementsByTagName("Госэкзамен").getLength(),
            "An empty state exam row produced a state exam entry"
        );
    }

    @Test
    void cannotProduceXmlWithoutStudents() {
        assertTrue(
            problems(() -> xml(pivot(), students())).contains("нет ни одной заполненной строки"),
            "An empty student file produced an XML without students"
        );
    }

    @Test
    void cannotSwapDayAndMonthOfIsoDate() throws Exception {
        assertEquals(
            "2001-02-03", first(xml(pivot(), students(student("ДатаРожд", "2001-02-03"))), "ДатаРожд"),
            "An ISO birth date had its day and month swapped"
        );
    }

    @Test
    void cannotMisreadRussianDateText() throws Exception {
        assertEquals(
            "2026-06-24", first(xml(pivot(), students(student("ДатаРешенияГэк", "24.06.2026"))), "ДатаРешенияГэк"),
            "A ДД.ММ.ГГГГ date was misread"
        );
    }

    @Test
    void cannotChangeLayoutCyberDiplomaReads() throws Exception {
        assertTrue(
            xml(pivot(), students(student())).startsWith(
                "<?xml version='1.0' encoding='utf-8'?>\n<ФайлОбменаКиберДиплом Версия=\"3.5.1\"><Студенты><Студент>"
                    + "<Фамилия>Иванов</Фамилия><Имя>Иван</Имя><Отчество>Иванович</Отчество>"
            ),
            "The XML no longer starts the way the Python service wrote it"
        );
    }
}
