package org.opendiplom.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.storage.Blanks;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Stock;

/**
 * The blanks of the documents of a graduation and their handing (ADR-0013):
 * the blanks of a printed run written in bulk, or one by one on the card, a
 * blank spoiled, and how a document was handed.
 */
final class Issuing {
    private static final String TITLE = "Бланки и выдача";

    private final Database database;

    Issuing(final Database database) {
        this.database = database;
    }

    /** The kinds a document is printed on. */
    static List<String> kinds(final DocumentRecord document) {
        return Stock.kinds(document.duplicate(), document.diplomaDuplicate, document.supplementDuplicate);
    }

    /** ГГГГ-ММ-ДД as ДД.ММ.ГГГГ; anything else as it is. */
    static String date(final String iso) {
        return iso != null && iso.matches("\\d{4}-\\d{2}-\\d{2}")
            ? iso.substring(8) + "." + iso.substring(5, 7) + "." + iso.substring(0, 4) : iso == null ? "" : iso;
    }

    /** Of each graduate, how far the original is: «—», «напечатан», «выдан 03.07.2026». */
    static List<String> states(final Registered registered) {
        final List<String> states = new ArrayList<>();
        for (final DocumentRecord original : registered.originals) {
            states.add(state(registered, original));
        }
        return states;
    }

    private static String state(final Registered registered, final DocumentRecord document) {
        if (document.id == null) {
            return "—";
        }
        final Stock.Issue issue = registered.issues.get(document.id);
        if (issue != null) {
            return "выдан";
        }
        final List<String> printed = new ArrayList<>();
        for (final String kind : kinds(document)) {
            if (registered.used(document.id, kind) != null) {
                printed.add(Blanks.KINDS.get(kind).toLowerCase(Locale.ROOT));
            }
        }
        return printed.isEmpty() ? "—" : printed.size() == kinds(document).size() ? "напечатан"
            : "на бланке: " + String.join(", ", printed);
    }

    /**
     * Documents ready to print without a blank of a kind, by the form of
     * blank they take, in the order of the list: what the print of the
     * graduation puts on the blanks.
     */
    static Map<Stock.Form, List<String>> waiting(final Registered registered) {
        final Map<Stock.Form, List<String>> waiting = new LinkedHashMap<>();
        for (int number = 0; number < registered.graduates.size(); ++number) {
            final DocumentRecord original = registered.originals.get(number);
            if (original.id == null || !Checks.ready(registered.printing.get(number))
                || registered.issues.containsKey(original.id)) {
                continue;
            }
            for (final String kind : kinds(original)) {
                if (registered.used(original.id, kind) == null) {
                    waiting.computeIfAbsent(registered.form(number, original, kind), any -> new ArrayList<>())
                        .add(original.id);
                }
            }
        }
        return waiting;
    }

    /** Originals on all their blanks and not handed yet. */
    static List<String> handable(final Registered registered) {
        final List<String> handable = new ArrayList<>();
        for (final DocumentRecord original : registered.originals) {
            if ("напечатан".equals(state(registered, original))) {
                handable.add(original.id);
            }
        }
        return handable;
    }

    /** The section of the graduation page: the blanks of the printed run and handing in person. */
    String graduation(final Registered registered) throws SQLException {
        final String id = registered.graduation.id;
        final List<String> states = states(registered);
        final long issued = states.stream().filter(state -> state.startsWith("выдан")).count();
        final long printed = states.stream().filter("напечатан"::equals).count() + issued;
        final StringBuilder html = new StringBuilder("<section><h2>").append(TITLE).append("</h2><p>На бланках: ")
            .append(printed).append(" из ").append(states.size()).append("; выдано: ").append(issued)
            .append(". <a href=\"stock\">Учёт бланков</a> · <a href=\"book\">Книга регистрации</a></p>");
        final Map<Stock.Form, List<String>> waiting = waiting(registered);
        if (!waiting.isEmpty()) {
            html.append("<form method=\"post\" action=\"graduations/").append(id).append("/blanks\">")
                .append("<p class=\"muted\">Положите в принтер бланки по возрастанию номеров и напечатайте документы по ")
                .append("ссылкам выше: печатаются ещё не записанные на бланки, по порядку списка. Затем укажите первый ")
                .append("бланк: бланки запишутся документам по порядку, мимо использованных и испорченных.</p>");
            final Stock stock = new Stock(this.database);
            for (final Map.Entry<Stock.Form, List<String>> form : waiting.entrySet()) {
                html.append(this.choice(stock, form.getKey(), form.getKey().title() + ": документов без бланка — "
                    + form.getValue().size(), "series." + form.getKey().code(), "first." + form.getKey().code()));
            }
            html.append("<br><button>Записать бланки</button></form>");
        }
        long unready = 0;
        for (int number = 0; number < registered.graduates.size(); ++number) {
            if (!Checks.ready(registered.printing.get(number)) && "—".equals(states.get(number))) {
                ++unready;
            }
        }
        if (unready > 0) {
            html.append("<p class=\"note\">Не готовы к печати: ").append(unready)
                .append(" — бланки им не записываются.</p>");
        }
        final List<String> handable = handable(registered);
        if (!handable.isEmpty()) {
            html.append("<form method=\"post\" action=\"graduations/").append(id).append("/issue\">")
                .append("<button>Выданы лично — все напечатанные и не выданные (").append(handable.size())
                .append(")</button></form><p class=\"muted\">По доверенности и почтой — в карточке выпускника. ")
                .append("Дата выдачи — та, что на бланке.</p>");
        }
        return html.append("</section>").toString();
    }

    /** The section of the card: the blanks of each document of the graduate and its handing. */
    String card(final Registered registered, final int number) throws SQLException {
        final GraduateRecord graduate = registered.graduates.get(number);
        final String action = "graduations/" + registered.graduation.id + "/graduates/" + graduate.id + "/blanks";
        final StringBuilder html = new StringBuilder("<section><h2>").append(TITLE).append("</h2>");
        final List<DocumentRecord> documents = new ArrayList<>();
        for (final DocumentRecord document : registered.documents.getOrDefault(graduate.id, List.of())) {
            if (!document.regNumber.isEmpty()) {
                documents.add(document);
            }
        }
        if (documents.isEmpty()) {
            return html.append("<p class=\"muted\">Бланки записываются документу с регистрационным номером.</p></section>")
                .toString();
        }
        final Stock stock = new Stock(this.database);
        boolean unhanded = false;
        for (final DocumentRecord document : documents) {
            final Stock.Issue issue = registered.issues.get(document.id);
            html.append("<h3>").append(Html.escape(document.duplicate()
                ? capital(document.duplicateTitle()) : "Диплом и приложение")).append(" № ")
                .append(Html.escape(document.regNumber)).append("</h3><table><tr><th>Бланк</th><th>Серия и номер</th>")
                .append("<th></th></tr>");
            boolean all = true;
            for (final String kind : kinds(document)) {
                final Stock.Form form = registered.form(number, document, kind);
                final Stock.Blank used = registered.used(document.id, kind);
                html.append("<tr><td>").append(Html.escape(form.title())).append("</td><td>");
                if (used != null) {
                    html.append(Html.escape(used.label())).append("</td><td>");
                    if (issue == null) {
                        unhanded = true;
                        html.append(form(action, "spoil", document.id, kind))
                            .append("<input type=\"text\" name=\"reason\" placeholder=\"почему испорчен\" required> ")
                            .append("<button>Бланк испорчен</button></form>");
                    }
                } else {
                    all = false;
                    html.append("не записан</td><td>");
                    if (stock.free(form).isEmpty()) {
                        html.append(this.choice(stock, form, null, "series", "number"));
                    } else {
                        html.append(form(action, "record", document.id, kind))
                            .append(this.choice(stock, form, null, "series", "number"))
                            .append(" <button>Записать</button></form>");
                    }
                }
                html.append("</td></tr>");
            }
            for (final Stock.Blank blank : registered.blanks.getOrDefault(document.id, List.of())) {
                if (Stock.SPOILED.equals(blank.status)) {
                    html.append("<tr class=\"muted\"><td>").append(Html.escape(blank.form.title())).append("</td><td>")
                        .append(Html.escape(blank.label())).append("</td><td>испорчен: ").append(Html.escape(blank.reason))
                        .append("</td></tr>");
                }
            }
            html.append("</table>");
            if (issue != null) {
                html.append("<p>Выдан ").append(Html.escape(issue.text())).append(".</p>").append(form(action, "cancel", document.id, ""))
                    .append("<button>Отменить запись о выдаче</button></form>");
            } else if (all) {
                html.append(form(action, "issue", document.id, ""))
                    .append("<label>Как выдан (п. 29 приказа № 670)</label>")
                    .append("<select name=\"way\">");
                for (final Map.Entry<String, String> way : Stock.WAYS.entrySet()) {
                    html.append("<option value=\"").append(way.getKey()).append("\">").append(Html.escape(way.getValue()))
                        .append("</option>");
                }
                html.append("</select><label>Номер и дата доверенности или почтового отправления</label>")
                    .append("<input type=\"text\" name=\"details\" placeholder=\"№ 12 от 01.07.2026\">")
                    .append("<br><button>Записать выдачу</button></form>");
            } else {
                html.append("<p class=\"muted\">Выдача записывается, когда записаны все бланки документа.</p>");
            }
        }
        if (unhanded) {
            html.append("<p class=\"note\">Пока документ напечатан и не выдан, правки ниже, меняющие напечатанное, ")
                .append("не принимаются: сначала отметьте бланк испорченным. ФИО и решение ГЭК есть на дипломе и на ")
                .append("приложении, остальное — на приложении.</p>");
        }
        return html.append("</section>").toString();
    }

    /** Writes the blanks of the printed run, for each form whose first blank came. */
    void blanks(final Registered registered, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final List<Stock.Run> runs = new ArrayList<>();
        for (final Map.Entry<Stock.Form, List<String>> form : waiting(registered).entrySet()) {
            final String first = field(request, "first." + form.getKey().code());
            if (!first.isEmpty()) {
                runs.add(new Stock.Run(form.getKey(), field(request, "series." + form.getKey().code()), first,
                    form.getValue()));
            }
        }
        if (runs.isEmpty()) {
            Responses.problem(response, TITLE, "Укажите первый бланк хотя бы одной формы");
            return;
        }
        final int recorded;
        try {
            recorded = new Stock(this.database).record(registered.graduation.id, runs);
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        }
        redirect(request, response, "graduations/" + registered.graduation.id, "Записано бланков: " + recorded);
    }

    /** Writes the handing in person of the documents printed and not handed. */
    void issue(final Registered registered, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final List<String> handable = handable(registered);
        try {
            new Stock(this.database).issue(registered.graduation.id, handable, Stock.PERSON, "");
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        }
        redirect(request, response, "graduations/" + registered.graduation.id, "Записана выдача документов: "
            + handable.size());
    }

    /** An action on a document of the card: write a blank, spoil one, write or cancel the handing. */
    void document(
        final Registered registered, final String graduate, final HttpServletRequest request,
        final HttpServletResponse response
    ) throws IOException, SQLException {
        final int number = registered.position(graduate);
        DocumentRecord document = null;
        for (final DocumentRecord own : number < 0 ? List.<DocumentRecord>of()
            : registered.documents.getOrDefault(graduate, List.of())) {
            if (own.id.equals(field(request, "document"))) {
                document = own;
            }
        }
        if (document == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final String kind = field(request, "kind");
        final String action = field(request, "action");
        final Stock stock = new Stock(this.database);
        final String id = registered.graduation.id;
        final String note;
        try {
            if ("record".equals(action) || "spoil".equals(action)) {
                if (!kinds(document).contains(kind)) {
                    throw new IllegalArgumentException("У документа нет бланка «" + kind + "»");
                }
                final Stock.Form form = registered.form(number, document, kind);
                if ("record".equals(action)) {
                    stock.record(id, document.id, form, field(request, "series"), field(request, "number"));
                    note = "Записан бланк: " + form.title() + " " + field(request, "series") + " " + field(request, "number");
                } else {
                    final Stock.Blank used = registered.used(document.id, kind);
                    if (used == null) {
                        throw new IllegalArgumentException("Документ не записан на бланк «" + form.title() + "»");
                    }
                    stock.spoil(used.form, used.series, used.number, field(request, "reason"));
                    note = "Бланк " + used.label() + " отмечен испорченным; документ можно напечатать заново";
                }
            } else if ("issue".equals(action)) {
                stock.issue(id, List.of(document.id), field(request, "way"), field(request, "details"));
                note = "Выдача записана";
            } else if ("cancel".equals(action)) {
                stock.cancel(id, document.id);
                note = "Запись о выдаче отменена";
            } else {
                throw new IllegalArgumentException("Действие «" + action + "» не известно");
            }
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        }
        redirect(request, response, "graduations/" + id + "/graduates/" + graduate, note);
    }

    /** The free blanks of a form: the series to choose and the first number, the lowest free one. */
    private String choice(
        final Stock stock, final Stock.Form form, final String label, final String series, final String first
    ) throws SQLException {
        final List<Stock.Free> free = stock.free(form);
        final StringBuilder html = new StringBuilder();
        if (label != null) {
            html.append("<label>").append(Html.escape(label)).append("</label>");
        }
        if (free.isEmpty()) {
            return html.append("<span class=\"note\">нет свободных бланков «").append(Html.escape(form.title()))
                .append("»: <a href=\"stock\">внесите приход</a></span>").toString();
        }
        html.append("<select name=\"").append(series).append("\">");
        for (final Stock.Free one : free) {
            html.append("<option value=\"").append(Html.escape(one.series)).append("\">серия ")
                .append(Html.escape(one.series)).append(" — свободно ").append(one.count).append(", первый ")
                .append(Html.escape(one.first)).append("</option>");
        }
        return html.append("</select> № <input type=\"text\" name=\"").append(first).append("\" value=\"")
            .append(Html.escape(free.get(0).first)).append("\" size=\"8\" style=\"width:7em\">").toString();
    }

    private static String form(final String action, final String what, final String document, final String kind) {
        return "<form method=\"post\" action=\"" + action + "\"><input type=\"hidden\" name=\"action\" value=\"" + what
            + "\"><input type=\"hidden\" name=\"document\" value=\"" + Html.escape(document)
            + "\"><input type=\"hidden\" name=\"kind\" value=\"" + Html.escape(kind) + "\">";
    }

    private static String capital(final String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static void redirect(
        final HttpServletRequest request, final HttpServletResponse response, final String page, final String note
    ) throws IOException {
        response.sendRedirect(request.getContextPath() + "/" + page + "?note="
            + URLEncoder.encode(note, StandardCharsets.UTF_8));
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
