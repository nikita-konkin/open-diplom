package org.opendiplom.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Honors;
import org.opendiplom.catalog.Organization;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.graduation.StudentMatch;
import org.opendiplom.graduation.SubjectMatch;
import org.opendiplom.imports.Kind;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;
import org.opendiplom.storage.Curricula;

/** Parts of the graduation pages. */
final class GraduationView {
    private GraduationView() {
    }

    /** The upload form of a new graduation. */
    static String upload(final String group) {
        return "<form method=\"post\" action=\"graduations/new\" enctype=\"multipart/form-data\">"
            + "<label>Группа</label><input type=\"text\" name=\"group\" value=\"" + Html.escape(group)
            + "\" required>"
            + "<label>Ведомость «Деканата» (.xls или .xlsx, лист на студента)</label>"
            + "<input type=\"file\" name=\"statement\" accept=\".xls,.xlsx\" required>"
            + "<label>Сведения о студентах (ФИО, даты, ГЭК, тема и оценка ВКР; для госэкзамена — колонка "
            + "«ОценкаГосэкзамен»)</label><input type=\"file\" name=\"info\" accept=\".xls,.xlsx\" required>"
            + "<br><button>Загрузить</button></form>";
    }

    /** The choice of the edition; the ones of the form and year of the statement come first. */
    static String curricula(final List<Curricula.Edition> editions, final String chosen, final String form, final Integer year) {
        final StringBuilder html = new StringBuilder("<section><h2>Учебный план</h2>");
        if (editions.isEmpty()) {
            html.append("<p class=\"error\">Нет учебного плана для формы «").append(Html.escape(form))
                .append("» и года набора ").append(year == null ? "?" : year)
                .append(". <a href=\"plans\">Загрузите его или создайте на основе плана другого года или формы</a>, ")
                .append("затем вернитесь сюда.</p>");
            return html.append("</section>").toString();
        }
        html.append("<select name=\"curriculum\"><option value=\"\">— выберите —</option>");
        String program = null;
        for (final Curricula.Edition edition : editions) {
            final boolean latest = !edition.programId.equals(program);
            program = edition.programId;
            html.append("<option value=\"").append(edition.id).append('"')
                .append(edition.id.equals(chosen) ? " selected" : "").append('>')
                .append(Html.escape(edition.header.code() + " " + edition.header.profile() + ", "
                    + edition.header.studyForm() + ", " + edition.header.year() + ", редакция " + edition.edition
                    + (latest ? " (последняя)" : "")))
                .append("</option>");
        }
        html.append("</select>");
        if (chosen != null) {
            html.append(" <a href=\"plans/").append(chosen).append("\">открыть план</a>");
        }
        return html.append("</section>").toString();
    }

    /** Students in the four groups of the report, each with the operator's choice. */
    static String students(final StudentMatch match, final List<String> sheets, final Map<String, String> choices) {
        final StringBuilder html = new StringBuilder("<section><h2>Студенты</h2><p>");
        for (final StudentMatch.Status status : StudentMatch.Status.values()) {
            final int count = match.with(status).size();
            if (count > 0) {
                html.append("<span class=\"").append(status.open() ? "note" : "muted").append("\">")
                    .append(Html.escape(status.title())).append(": ").append(count).append("</span> · ");
            }
        }
        html.append("</p><div class=\"scroll\"><table><tr><th>Сведения о студентах</th><th>Лист ведомости</th>")
            .append("<th>Статус</th><th>Связать</th></tr>");
        for (final StudentMatch.Pair pair : match.pairs()) {
            html.append("<tr").append(pair.status.open() ? " class=\"error\"" : "").append("><td>")
                .append(pair.entry == null ? "—" : Html.escape(pair.entry.fullName + " (строка " + pair.entry.line + ")"))
                .append("</td><td>").append(pair.student == null ? "—" : Html.escape(pair.student))
                .append("</td><td>").append(Html.escape(pair.status.title()));
            if (!pair.candidates.isEmpty() && pair.status == StudentMatch.Status.AMBIGUOUS) {
                html.append(": ").append(Html.escape(String.join(", ", pair.candidates)));
            }
            html.append("</td><td><select name=\"").append(Html.escape(pair.item())).append("\">")
                .append(option("", "автоматически", choices.get(pair.item())));
            if (pair.entry != null) {
                final List<String> ordered = new ArrayList<>(pair.candidates);
                for (final String sheet : sheets) {
                    if (!ordered.contains(sheet)) {
                        ordered.add(sheet);
                    }
                }
                for (final String sheet : ordered) {
                    html.append(option(sheet, sheet, choices.get(pair.item())));
                }
            }
            html.append(option(StudentMatch.EXCLUDED, "не включать в выпуск", choices.get(pair.item())))
                .append("</select></td></tr>");
        }
        return html.append("</table></div></section>").toString();
    }

    /** Subjects with their elements, statuses and notes. */
    static String subjects(final SubjectMatch match) {
        final StringBuilder html = new StringBuilder("<section><h2>Предметы ведомости → элементы плана</h2><p>")
            .append("Автоматически: ").append(match.automatic()).append(" из ").append(match.rows().size())
            .append(". Связи, выбранные или подтверждённые вручную, запоминаются для программы.</p>")
            .append("<div class=\"scroll\"><table><tr><th>Предмет ведомости</th><th>Вид</th><th>З.е. по часам</th>")
            .append("<th>Студентов</th><th>Статус</th><th>Элемент плана</th><th>Проверить</th></tr>");
        for (final SubjectMatch.Row row : match.rows()) {
            final String current = row.target == null ? "" : row.target.code();
            html.append("<tr").append(row.status.open() ? " class=\"error\"" : "").append("><td>")
                .append(Html.escape(row.subject.name)).append("</td><td>").append(Html.escape(row.subject.kind.title()))
                .append("</td><td>").append(row.subject.kind == Kind.COURSE_WORK ? "—" : String.valueOf(row.subject.counted))
                .append("</td><td>").append(row.subject.students).append("</td><td>")
                .append(Html.escape(row.status.title())).append("</td><td><select name=\"")
                .append(Html.escape(SubjectMatch.PREFIX + row.subject.key())).append("\">")
                .append(option("", "— выберите —", current));
            for (final SubjectMatch.Target target : match.options(row.subject.kind)) {
                html.append(option(target.code(), element(match.plan(), target), current));
            }
            html.append(option(StudentMatch.EXCLUDED, "не включать в приложение", current))
                .append("</select></td><td class=\"note\">").append(Html.escape(String.join("; ", row.notes)))
                .append("</td></tr>");
        }
        return html.append("</table></div></section>").toString();
    }

    /** «Б.1.1.3 Физика (4 з.е.)» or the chosen elective. */
    static String element(final PlanStructure plan, final SubjectMatch.Target target) {
        final PlanItem item = plan.items().get(target.position);
        final String name = target.alternative.isEmpty() ? item.printed() : target.alternative + " — " + item.row().name();
        return (item.row().index() + " " + name).strip()
            + (item.row().credits() == null ? "" : " (" + PlanTotals.number(item.row().credits()) + " з.е.)");
    }

    /** Graduates with how far each is from the XML and from the printed diploma. */
    static String graduates(
        final String graduation, final List<GraduateRecord> graduates, final List<List<Checks.Finding>> findings,
        final List<DocumentRecord> originals, final List<Honors> honors, final List<List<Checks.Finding>> printing
    ) {
        final StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><tr><th>№</th><th>ФИО</th>")
            .append("<th>Номер студента</th><th>Результатов</th><th>Ошибок</th><th>Не завершено</th>")
            .append("<th>Предупреждений</th><th>Итог</th><th>Рег. номер</th><th>С отличием</th><th>К печати</th></tr>");
        for (int number = 0; number < graduates.size(); ++number) {
            final GraduateRecord graduate = graduates.get(number);
            final List<Checks.Finding> own = findings.get(number);
            final long errors = Checks.count(own, Checks.Level.ERROR);
            final DocumentRecord document = originals.get(number);
            final List<Checks.Finding> print = printing.get(number);
            html.append("<tr><td>").append(number + 1).append("</td><td><a href=\"graduations/").append(graduation)
                .append("/graduates/").append(graduate.id).append("\">").append(Html.escape(graduate.fullName()))
                .append("</a></td><td>").append(Html.escape(graduate.studentNumber))
                .append("</td><td>").append(graduate.results.size())
                .append("</td><td").append(errors > 0 ? " class=\"error\"" : "").append(">").append(errors)
                .append("</td><td>").append(Checks.count(own, Checks.Level.UNFINISHED))
                .append("</td><td>").append(Checks.count(own, Checks.Level.WARNING))
                .append("</td><td>").append(Checks.ready(own) ? "готов" : Checks.exportable(own) ? "не завершено" : "ошибки")
                .append("</td><td>").append(Html.escape(document.regNumber.isEmpty() ? "—" : document.regNumber))
                .append("</td><td>").append(Html.escape(honors(document, honors.get(number))))
                .append("</td><td").append(Checks.ready(print) ? ">готов" : " class=\"note\">замечаний: "
                    + (Checks.count(print, Checks.Level.ERROR) + Checks.count(print, Checks.Level.UNFINISHED)))
                .append("</td></tr>");
        }
        return html.append("</table></div>").toString();
    }

    /** «да», «нет», «да (вручную)», «?» when the rule cannot say yet. */
    static String honors(final DocumentRecord document, final Honors rule) {
        if (document.honors != null) {
            return (document.honors ? "да" : "нет") + " (вручную)";
        }
        return rule.proposal == null ? "?" : rule.proposal ? "да" : "нет";
    }

    /** The numbers and the date of issue of a graduation, and what keeps its documents from printing. */
    static String documents(
        final String graduation, final Organization organization, final List<List<Checks.Finding>> printing,
        final String note
    ) {
        final long ready = printing.stream().filter(Checks::ready).count();
        final StringBuilder html = new StringBuilder("<section><h2>Документы</h2>");
        if (note != null) {
            html.append("<p class=\"note\">").append(Html.escape(note)).append("</p>");
        }
        if (!organization.missing().isEmpty()) {
            html.append("<p class=\"error\">Не заполнены <a href=\"organization\">данные вуза</a>: ")
                .append(Html.escape(String.join(", ", organization.missing()))).append(".</p>");
        }
        html.append("<p>Готовы к печати: ").append(ready).append(" из ").append(printing.size())
            .append(". Печать на бланке появится на следующем этапе.</p>")
            .append("<form method=\"post\" action=\"graduations/").append(graduation).append("/numbers\">")
            .append("<label>Регистрационные номера — подряд или с пропусками: «10001–10007, 10010». ")
            .append("Получат выпускники без номера, по порядку списка</label>")
            .append("<input type=\"text\" name=\"numbers\">")
            .append("<label>Дата выдачи — для всех документов выпуска</label><input type=\"date\" name=\"issue_date\">")
            .append("<br><button>Присвоить</button></form></section>");
        return html.toString();
    }

    /** The document of a graduate: number, date, «с отличием» with its reckoning, findings and duplicates. */
    static String document(
        final String graduation, final GraduateRecord graduate, final DocumentRecord original, final Honors rule,
        final List<Checks.Finding> printing, final List<DocumentRecord> all
    ) {
        final String action = "graduations/" + graduation + "/graduates/" + graduate.id;
        final String proposal = rule.proposal == null ? "не рассчитано" : rule.proposal ? "с отличием" : "без отличия";
        final String current = original.honors == null ? "" : original.honors ? "1" : "0";
        final StringBuilder html = new StringBuilder("<section><h2>Документ</h2><p class=\"muted\">")
            .append("Диплом и приложение: один регистрационный номер и одна дата выдачи.</p>")
            .append("<form method=\"post\" action=\"").append(action).append("\">")
            .append("<label>Регистрационный номер</label><input type=\"text\" name=\"reg_number\" value=\"")
            .append(Html.escape(original.regNumber)).append("\">")
            .append("<label>Дата выдачи</label><input type=\"date\" name=\"issue_date\" value=\"")
            .append(Html.escape(original.issueDate)).append("\">")
            .append("<label>С отличием (п. 27 приказа № 670): ").append(Html.escape(rule.explanation)).append("</label>")
            .append("<select name=\"honors\">")
            .append(option("", "по расчёту: " + proposal, current))
            .append(option("1", "с отличием — решение оператора", current))
            .append(option("0", "без отличия — решение оператора", current))
            .append("</select><br><button>Сохранить</button></form>")
            .append("<h3>Проверка для печати</h3>").append(findings(printing));
        final List<DocumentRecord> duplicates = new ArrayList<>();
        for (final DocumentRecord document : all) {
            if (document.duplicate()) {
                duplicates.add(document);
            }
        }
        html.append("<h3>Дубликаты</h3>");
        if (duplicates.isEmpty()) {
            html.append("<p class=\"muted\">Не выдавались.</p>");
        } else {
            html.append("<table><tr><th>Что</th><th>Рег. номер</th><th>Дата выдачи</th></tr>");
            for (final DocumentRecord duplicate : duplicates) {
                html.append("<tr><td>").append(Html.escape(duplicate.duplicateTitle())).append("</td><td>")
                    .append(Html.escape(duplicate.regNumber)).append("</td><td>").append(Html.escape(duplicate.issueDate))
                    .append("</td></tr>");
            }
            html.append("</table>");
        }
        if (!original.regNumber.isEmpty()) {
            html.append("<form method=\"post\" action=\"").append(action).append("/duplicate\">")
                .append("<label>Выдать дубликат взамен № ").append(Html.escape(original.regNumber)).append("</label>")
                .append("<select name=\"kind\"><option value=\"both\">диплома и приложения</option>")
                .append("<option value=\"supplement\">только приложения</option>")
                .append("<option value=\"diploma\">только диплома</option></select>")
                .append("<label>Регистрационный номер дубликата</label><input type=\"text\" name=\"reg_number\">")
                .append("<label>Дата выдачи дубликата</label><input type=\"date\" name=\"issue_date\">")
                .append("<br><button>Выдать дубликат</button></form>");
        }
        return html.append("</section>").toString();
    }

    /** Findings as a list, errors first. */
    static String findings(final List<Checks.Finding> findings) {
        if (findings.isEmpty()) {
            return "<p class=\"muted\">Замечаний нет.</p>";
        }
        final StringBuilder html = new StringBuilder("<ul>");
        for (final Checks.Finding finding : findings) {
            html.append("<li").append(finding.level == Checks.Level.ERROR ? " class=\"error\""
                    : finding.level == Checks.Level.UNFINISHED ? " class=\"note\"" : "").append('>')
                .append(Html.escape(finding.level.title())).append(": ").append(Html.escape(finding.message))
                .append("</li>");
        }
        return html.append("</ul>").toString();
    }

    /** The personal data, the attestation and the results of a graduate. */
    static String card(final GraduateRecord graduate, final PlanStructure plan) {
        final StringBuilder html = new StringBuilder("<section><h2>Сведения</h2><table>")
            .append(row("ФИО", graduate.fullName()))
            .append(row("Дата рождения", graduate.birthDate))
            .append(row("Документ о предыдущем образовании", graduate.previousDocument))
            .append(row("Год документа", graduate.previousYear))
            .append(row("Дата решения ГЭК", graduate.gekDate))
            .append(row("Номер протокола ГЭК", graduate.gekProtocol))
            .append(row("Лист ведомости", graduate.statementName))
            .append(row("Номер студента", graduate.studentNumber))
            .append("</table>");
        if (!graduate.notes.isEmpty()) {
            html.append("<p class=\"note\">При чтении файла сведений: ").append(Html.escape(graduate.notes)).append("</p>");
        }
        html.append("</section><section><h2>Государственная итоговая аттестация</h2><table>")
            .append(row("Государственный экзамен", grade(graduate.stateExamGrade)))
            .append(row("Тема ВКР", graduate.thesisTopic))
            .append(row("Оценка ВКР", grade(graduate.thesisGrade)))
            .append("</table></section><section><h2>Результаты</h2><div class=\"scroll\"><table><tr>")
            .append("<th>Индекс</th><th>В приложении</th><th>Вид</th><th>Оценка</th><th>З.е.</th></tr>");
        for (final ResultRecord result : graduate.results) {
            html.append("<tr><td>").append(Html.escape(plan.items().get(result.element).row().index()))
                .append("</td><td>").append(Html.escape(result.printed))
                .append("</td><td>").append(Html.escape(result.kind))
                .append("</td><td>").append(Html.escape(result.grade == null ? result.gradeText : grade(result.grade)))
                .append("</td><td>").append(result.credits == null ? "" : PlanTotals.number(result.credits))
                .append("</td></tr>");
        }
        return html.append("</table></div></section>").toString();
    }

    /** «5», «зачтено (6)», «не выполнял (7)», empty for none. */
    static String grade(final Integer grade) {
        if (grade == null) {
            return "";
        }
        return grade == 6 ? "зачтено (6)" : grade == 7 ? "не выполнял (7)" : String.valueOf(grade);
    }

    private static String row(final String name, final String value) {
        return "<tr><th>" + Html.escape(name) + "</th><td>" + Html.escape(value) + "</td></tr>";
    }

    private static String option(final String value, final String label, final String current) {
        return "<option value=\"" + Html.escape(value) + "\"" + (value.equals(current == null ? "" : current)
            ? " selected" : "") + ">" + Html.escape(label) + "</option>";
    }
}
