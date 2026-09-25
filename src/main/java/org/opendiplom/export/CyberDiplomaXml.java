package org.opendiplom.export;

import java.util.List;
import org.opendiplom.sheets.Cells;

/**
 * Writes «ФайлОбменаКиберДиплом» version 3.5.1, the file CyberDiploma imports.
 *
 * <p>The layout repeats the Python service byte for byte (declaration,
 * no indentation, {@code <Тег />} for empty elements), so the two can be
 * compared on real data during the transition.
 */
public final class CyberDiplomaXml {
    private final StringBuilder xml = new StringBuilder();

    private CyberDiplomaXml() {
    }

    public static String write(final Program program, final List<Graduate> graduates) {
        final CyberDiplomaXml out = new CyberDiplomaXml();
        out.xml.append("<?xml version='1.0' encoding='utf-8'?>\n");
        out.xml.append("<ФайлОбменаКиберДиплом Версия=\"3.5.1\">");
        if (graduates.isEmpty()) {
            out.xml.append("<Студенты />");
        } else {
            out.open("Студенты");
            for (final Graduate graduate : graduates) {
                out.student(program, graduate);
            }
            out.close("Студенты");
        }
        out.xml.append("</ФайлОбменаКиберДиплом>");
        return out.xml.toString();
    }

    private void student(final Program program, final Graduate graduate) {
        this.open("Студент");
        this.text("Фамилия", graduate.lastName);
        this.text("Имя", graduate.firstName);
        this.text("Отчество", graduate.middleName);
        this.text("ДатаРожд", graduate.birthDate);
        this.text("НаименованиеДокПредОбр", graduate.previousDocument);
        this.text("ГодДокПредОбр", graduate.previousYear);
        this.text("ДатаРешенияГэк", graduate.gekDate);
        this.text("НомерПротоколаГэк", graduate.gekProtocol);
        this.open("Госэкзамены");
        this.open("Заголовок");
        this.text("ЗачЕд", program.finalCredits);
        this.close("Заголовок");
        for (final Integer grade : graduate.stateExams) {
            this.exam(PivotSource.STATE_EXAM, grade);
        }
        this.exam("Выпускная квалификационная работа \"" + graduate.thesisTopic + "\"", graduate.thesisGrade);
        for (final Graduate.Result result : graduate.results) {
            if ("госэкзамен".equals(result.type)) {
                this.exam(result.name, result.grade);
            }
        }
        this.close("Госэкзамены");
        this.open("ОбъемОбрПрограммы");
        this.text("ЗачЕд", program.programCredits);
        this.close("ОбъемОбрПрограммы");
        this.open("ОбъемАудиторныхЧасов");
        this.text("ЧасНед", program.contactHours);
        this.close("ОбъемАудиторныхЧасов");
        this.text("Квалификация", program.qualification);
        this.text("СрокОбучения", program.studyTerm);
        this.text("ПредседательГэк", program.gekChairman);
        this.text("НаименованиеСпец", program.directionName);
        this.text("КодСпец", program.directionCode);
        this.open("ДополнительныеСведения");
        this.text("ДопСвед", "Направленность (профиль) образовательной программы: \"" + program.profile + "\"");
        this.text("ДопСвед", "Форма обучения: " + program.studyForm);
        this.close("ДополнительныеСведения");
        this.section(graduate, "Курсовые", null, "курсовая", "КурсоваяРабота", false);
        this.section(graduate, "Практики", program.practiceCredits, "практика", "Практика", true);
        this.section(graduate, "Факультативы", null, "факультатив", "Факультатив", true);
        this.section(graduate, "Дисциплины", null, "дисциплина", "Дисциплина", true);
        this.close("Студент");
    }

    private void section(
        final Graduate graduate, final String tag, final Object header,
        final String type, final String item, final boolean credits
    ) {
        final boolean empty = header == null
            && graduate.results.stream().noneMatch(r -> type.equals(r.type));
        if (empty) {
            this.xml.append('<').append(tag).append(" />");
            return;
        }
        this.open(tag);
        if (header != null) {
            this.open("Заголовок");
            this.text("ЗачЕд", header);
            this.close("Заголовок");
        }
        for (final Graduate.Result result : graduate.results) {
            if (type.equals(result.type)) {
                this.open(item);
                this.text("Наименование", result.name);
                this.text("Оценка", result.grade);
                if (credits) {
                    this.text("ЗачЕд", result.credits);
                }
                this.close(item);
            }
        }
        this.close(tag);
    }

    private void exam(final String name, final Integer grade) {
        this.open("Госэкзамен");
        this.text("Наименование", name);
        this.text("Оценка", grade);
        this.close("Госэкзамен");
    }

    private void open(final String tag) {
        this.xml.append('<').append(tag).append('>');
    }

    private void close(final String tag) {
        this.xml.append("</").append(tag).append('>');
    }

    private void text(final String tag, final Object value) {
        final String text = value instanceof Integer ? value.toString() : Cells.text(value);
        if (text.isEmpty()) {
            this.xml.append('<').append(tag).append(" />");
            return;
        }
        this.open(tag);
        this.xml.append(text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        this.close(tag);
    }
}
