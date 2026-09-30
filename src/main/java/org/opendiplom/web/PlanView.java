package org.opendiplom.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendiplom.plans.PlanCheck;
import org.opendiplom.plans.PlanDiff;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;

/** Parts of the plan pages: the title, the totals, the sums, the changes and the table. */
final class PlanView {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private PlanView() {
    }

    static String header(final PlanHeader header) {
        return "<table>"
            + row("Направление", header.code() + " " + header.direction())
            + row("Профиль", header.profile())
            + row("Квалификация", header.qualification())
            + row("Форма обучения", header.studyForm())
            + row("Срок обучения", header.studyTerm())
            + row("Год набора", header.year())
            + "</table>";
    }

    /** Volumes a supplement prints, and why one is missing. */
    static String totals(final List<PlanRow> rows) {
        final PlanTotals totals = PlanTotals.of(rows);
        final StringBuilder html = new StringBuilder("<table>")
            .append(row("Объём программы, з.е.", number(totals.program())))
            .append(row("Практики, з.е.", number(totals.practices())))
            .append(row("ГИА, з.е.", number(totals.attestation())))
            .append(row("Контактная работа, ак. ч", number(totals.contact())))
            .append("</table>");
        for (final String problem : totals.problems()) {
            html.append("<p class=\"note\">").append(Html.escape(problem)).append("</p>");
        }
        return html.toString();
    }

    static String check(final PlanCheck check) {
        if (check.findings().isEmpty()) {
            return "<p>Контрольные суммы сходятся: строки, части, блоки и объём программы.</p>";
        }
        final StringBuilder html = new StringBuilder();
        html.append("<p>").append(check.passed() ? "Суммы сходятся, есть замечания:" : "Ошибок: " + check.errors())
            .append("</p><ul>");
        for (final PlanCheck.Finding finding : check.findings()) {
            html.append("<li class=\"").append(finding.level() == PlanCheck.Level.ERROR ? "error" : "note")
                .append("\">").append(Html.escape(finding.message())).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    /**
     * Changes from another plan, or that there are none.
     *
     * @param against the link to the other plan, HTML
     */
    static String diff(final PlanDiff diff, final String against) {
        if (diff.same()) {
            return "<p>Совпадает с планом " + against + " по элементам, з.е., формам контроля и итогам.</p>";
        }
        final StringBuilder html = new StringBuilder("<p>Отличия от плана ").append(against).append(":</p><ul>");
        for (final PlanDiff.Change change : diff.changes()) {
            html.append("<li>").append(kind(change.kind()));
            if (!change.name().isEmpty()) {
                html.append(" «").append(Html.escape(change.name())).append("»");
            }
            if (!change.details().isEmpty()) {
                html.append(": ").append(Html.escape(String.join("; ", change.details())));
            }
            html.append("</li>");
        }
        return html.append("</ul>").toString();
    }

    /** The plan as a table, the rows with findings marked. */
    static String table(final PlanStructure plan, final PlanCheck check) {
        final Map<Integer, String> marks = new HashMap<>();
        for (final PlanCheck.Finding finding : check.findings()) {
            marks.merge(
                finding.position(), finding.level() == PlanCheck.Level.ERROR ? "error" : "note",
                (left, right) -> "error".equals(left) ? left : right
            );
        }
        final StringBuilder html = new StringBuilder("<div class=\"scroll\"><table><tr><th>Индекс</th>")
            .append("<th>Наименование</th><th>Вид</th><th>Экз.</th><th>Зач.</th><th>Зач. с оц.</th><th>КП</th>")
            .append("<th>КР</th><th>З.е.</th><th>З.е. экз.</th><th>З.е. зан.</th><th>Часы</th><th>Ч. экз.</th>")
            .append("<th>Ч. зан.</th><th>Конт.</th><th>СРС</th></tr>");
        for (final PlanItem item : plan.items()) {
            final PlanRow row = item.row();
            final boolean heading = item.kind() != PlanItem.Kind.ELEMENT;
            html.append("<tr").append(marks.containsKey(item.position())
                ? " class=\"" + marks.get(item.position()) + "\"" : "").append("><td>")
                .append(Html.escape(row.index())).append("</td><td style=\"padding-left:")
                .append(6 + 14 * depth(plan, item)).append("px\">")
                .append(heading ? "<b>" : "").append(Html.escape(row.name())).append(heading ? "</b>" : "");
            if (!item.printed().equals(row.name())) {
                html.append("<br><span class=\"muted\">в приложении: ").append(Html.escape(item.printed()))
                    .append("</span>");
            }
            html.append("</td><td>").append(item.leaf() ? section(item.section()) : "").append("</td>");
            for (final String control : row.controls()) {
                html.append("<td>").append(Html.escape(control)).append("</td>");
            }
            for (final Double value : row.creditColumns()) {
                html.append("<td>").append(number(value)).append("</td>");
            }
            for (final Double value : row.hours()) {
                html.append("<td>").append(number(value)).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</table></div>").toString();
    }

    /** «29.09.2026 01:37» in the time zone of the computer, from a saved instant. */
    static String time(final String instant) {
        try {
            return TIME.format(Instant.parse(instant).atZone(ZoneId.systemDefault()));
        } catch (final DateTimeParseException error) {
            return instant;
        }
    }

    static String number(final Double value) {
        return value == null ? "" : PlanTotals.number(value);
    }

    static String section(final PlanItem.Section section) {
        switch (section) {
            case DISCIPLINES:
                return "дисциплина";
            case PRACTICES:
                return "практика";
            case ATTESTATION:
                return "ГИА";
            case FACULTATIVES:
                return "факультатив";
            default:
                return "";
        }
    }

    private static String kind(final PlanDiff.Kind kind) {
        switch (kind) {
            case ADDED:
                return "Добавлено";
            case REMOVED:
                return "Удалено";
            case CHANGED:
                return "Изменено";
            default:
                return "Итоги";
        }
    }

    private static int depth(final PlanStructure plan, final PlanItem item) {
        int depth = 0;
        for (int parent = item.parent(); parent >= 0; parent = plan.items().get(parent).parent()) {
            ++depth;
        }
        return depth;
    }

    private static String row(final String name, final String value) {
        return "<tr><th>" + Html.escape(name) + "</th><td>" + Html.escape(value) + "</td></tr>";
    }
}
