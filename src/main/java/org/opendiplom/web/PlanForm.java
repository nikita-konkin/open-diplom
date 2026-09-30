package org.opendiplom.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;

/**
 * A plan typed in or corrected by hand: the title and every cell of every
 * row, with empty rows to add and a box to delete. The order field puts a
 * new row between others: 125 goes between 120 and 130.
 */
final class PlanForm {
    /** Empty rows under the plan for new elements. */
    static final int SPARE = 5;
    private static final int STEP = 10;
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final String[] FORMS = {"очная", "заочная", "очно-заочная"};

    private final PlanHeader header;
    private final List<PlanRow> rows;
    private final String note;
    private final List<String> problems;

    PlanForm(final PlanHeader header, final List<PlanRow> rows, final String note, final List<String> problems) {
        this.header = header;
        this.rows = rows;
        this.note = note;
        this.problems = problems;
    }

    /** The plan as the operator sent it; what cannot be read goes to {@link #problems()}. */
    static PlanForm read(final HttpServletRequest request) {
        final PlanHeader header = new PlanHeader(
            field(request, "code"), field(request, "direction"), field(request, "profile"),
            field(request, "qualification"), field(request, "form"), field(request, "term"), field(request, "year")
        );
        final List<String> problems = new ArrayList<>();
        final List<Double> orders = new ArrayList<>();
        final List<PlanRow> rows = new ArrayList<>();
        int count;
        try {
            count = Integer.parseInt(field(request, "rows"));
        } catch (final NumberFormatException error) {
            count = 0;
        }
        for (int row = 0; row < count; ++row) {
            final String prefix = "r" + row + ".";
            if (!field(request, prefix + "delete").isEmpty()) {
                continue;
            }
            final String index = field(request, prefix + "index");
            final String name = field(request, prefix + "name");
            final List<String> controls = new ArrayList<>();
            for (int form = 0; form < PlanRow.CONTROLS; ++form) {
                controls.add(PlanRow.semesters(field(request, prefix + "c" + form)));
            }
            final List<Double> credits = numbers(request, prefix + "u", PlanRow.CREDITS, row, problems);
            final List<Double> hours = numbers(request, prefix + "h", PlanRow.HOURS, row, problems);
            final boolean empty = index.isEmpty() && name.isEmpty() && String.join("", controls).isEmpty()
                && credits.stream().allMatch(value -> value == null) && hours.stream().allMatch(value -> value == null);
            if (empty) {
                continue;
            }
            if (name.isEmpty()) {
                problems.add("Строка " + (row + 1) + ": нет наименования");
            }
            final String order = field(request, prefix + "order").replace(',', '.');
            orders.add(NUMBER.matcher(order).matches() ? Double.parseDouble(order) : (double) (row + 1) * STEP);
            rows.add(new PlanRow(index, name, controls, credits, hours));
        }
        final List<Integer> sorted = new ArrayList<>();
        for (int row = 0; row < rows.size(); ++row) {
            sorted.add(row);
        }
        // a stable sort keeps rows of equal order as they were
        sorted.sort((left, right) -> Double.compare(orders.get(left), orders.get(right)));
        final List<PlanRow> ordered = new ArrayList<>();
        for (final int row : sorted) {
            ordered.add(rows.get(row));
        }
        return new PlanForm(header, ordered, field(request, "note"), problems);
    }

    PlanHeader header() {
        return this.header;
    }

    List<PlanRow> rows() {
        return this.rows;
    }

    String note() {
        return this.note;
    }

    /** Cells that are not numbers and rows without a name. */
    List<String> problems() {
        return Collections.unmodifiableList(this.problems);
    }

    /**
     * The form posting to an address; hidden fields go first, as pairs of
     * name and value.
     */
    String html(final String action, final String... hidden) {
        final StringBuilder html = new StringBuilder("<form method=\"post\" action=\"")
            .append(Html.escape(action)).append("\">");
        for (int pair = 0; pair + 1 < hidden.length; pair += 2) {
            html.append(input("hidden", hidden[pair], hidden[pair + 1], 0));
        }
        html.append("<section><h2>Шапка</h2>")
            .append(labelled("Код направления", "code", this.header.code()))
            .append(labelled("Наименование направления", "direction", this.header.direction()))
            .append(labelled("Профиль", "profile", this.header.profile()))
            .append(labelled("Квалификация", "qualification", this.header.qualification()))
            .append("<label>Форма обучения</label>")
            .append(input("text", "form", this.header.studyForm(), 0).replace(">", " list=\"forms\">"))
            .append("<datalist id=\"forms\">");
        for (final String form : FORMS) {
            html.append("<option value=\"").append(form).append("\">");
        }
        html.append("</datalist>")
            .append(labelled("Срок обучения", "term", this.header.studyTerm()))
            .append(labelled("Год набора", "year", this.header.year()))
            .append(labelled("Что изменено (для журнала)", "note", this.note))
            .append("</section>");
        html.append("<section><h2>Строки</h2><p class=\"muted\">Порядок: чтобы вставить строку между 120 и 130, ")
            .append("укажите 125. Семестры — через запятую. Пустая строка не сохраняется.</p>")
            .append("<div class=\"scroll\"><table><tr><th>Порядок</th><th>Индекс</th><th>Наименование</th>")
            .append("<th>Экз.</th><th>Зач.</th><th>Зач. с оц.</th><th>КП</th><th>КР</th><th>З.е.</th>")
            .append("<th>З.е. экз.</th><th>З.е. зан.</th><th>Часы</th><th>Ч. экз.</th><th>Ч. зан.</th>")
            .append("<th>Конт.</th><th>СРС</th><th>Удалить</th></tr>");
        final int count = this.rows.size() + SPARE;
        for (int row = 0; row < count; ++row) {
            final PlanRow plan = row < this.rows.size() ? this.rows.get(row) : null;
            final String prefix = "r" + row + ".";
            html.append("<tr><td>").append(input("text", prefix + "order", plan == null ? "" : String.valueOf((row + 1) * STEP), 4))
                .append("</td><td>").append(input("text", prefix + "index", plan == null ? "" : plan.index(), 8))
                .append("</td><td>").append(input("text", prefix + "name", plan == null ? "" : plan.name(), 40))
                .append("</td>");
            for (int form = 0; form < PlanRow.CONTROLS; ++form) {
                html.append("<td>").append(input("text", prefix + "c" + form, plan == null ? "" : plan.controls().get(form), 4))
                    .append("</td>");
            }
            for (int column = 0; column < PlanRow.CREDITS; ++column) {
                html.append("<td>").append(input("text", prefix + "u" + column,
                    plan == null ? "" : PlanView.number(plan.creditColumns().get(column)), 3)).append("</td>");
            }
            for (int column = 0; column < PlanRow.HOURS; ++column) {
                html.append("<td>").append(input("text", prefix + "h" + column,
                    plan == null ? "" : PlanView.number(plan.hours().get(column)), 4)).append("</td>");
            }
            html.append("<td>").append(plan == null ? "" : "<input type=\"checkbox\" name=\"" + prefix + "delete\" value=\"1\">")
                .append("</td></tr>");
        }
        html.append("</table></div>").append(input("hidden", "rows", String.valueOf(count), 0)).append("</section>")
            .append("<p><label><input type=\"checkbox\" name=\"force\" value=\"1\"> Сохранить, даже если суммы ")
            .append("не сходятся</label></p>")
            .append("<button name=\"action\" value=\"check\">Проверить</button> ")
            .append("<button name=\"action\" value=\"save\">Сохранить новой редакцией</button></form>");
        return html.toString();
    }

    private static List<Double> numbers(
        final HttpServletRequest request, final String prefix, final int count, final int row,
        final List<String> problems
    ) {
        final List<Double> numbers = new ArrayList<>();
        for (int column = 0; column < count; ++column) {
            final String text = field(request, prefix + column);
            if (text.isEmpty()) {
                numbers.add(null);
            } else if (NUMBER.matcher(text).matches()) {
                numbers.add(Double.valueOf(text.replace(',', '.')));
            } else {
                numbers.add(null);
                problems.add("Строка " + (row + 1) + ": «" + text + "» — не число");
            }
        }
        return numbers;
    }

    private static String labelled(final String label, final String name, final String value) {
        return "<label>" + Html.escape(label) + "</label>" + input("text", name, value, 0);
    }

    private static String input(final String type, final String name, final String value, final int size) {
        return "<input type=\"" + type + "\" name=\"" + Html.escape(name) + "\" value=\"" + Html.escape(value) + "\""
            + (size > 0 ? " size=\"" + size + "\" style=\"width:auto\"" : "") + ">";
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
