package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Stock;

/**
 * Blanks of strict accounting (ADR-0013; Order No 670, item 47): what came
 * from the typographer, how many are free, used and spoiled, the spoiled
 * ones with the reason, and the register of all of them as XLSX.
 */
final class StockPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Бланки строгой отчётности";
    private static final String[] REGISTER = {
        "Форма бланка", "Серия", "Номер", "Получен", "Состояние", "Рег. номер документа", "Причина порчи", "Изменён",
    };
    private static final int[] WIDTHS = {30, 9, 10, 12, 13, 14, 40, 22};

    private final transient Database database;

    StockPage(final Database database) {
        this.database = database;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        try {
            final Stock stock = new Stock(this.database);
            if ("xlsx".equals(request.getParameter("format"))) {
                final List<String[]> rows = new ArrayList<>();
                for (final Stock.Blank blank : stock.all()) {
                    rows.add(new String[] {
                        blank.form.title(), blank.series, blank.number, Issuing.date(blank.received), blank.state(),
                        blank.regNumber, blank.reason, PlanView.time(blank.changedAt),
                    });
                }
                Responses.file(response, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "Реестр бланков " + LocalDate.now() + ".xlsx",
                    Spreadsheet.of("Реестр бланков", "Реестр бланков строгой отчётности на " + Issuing.date(
                        LocalDate.now().toString()), REGISTER, WIDTHS, rows));
                return;
            }
            Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, page(stock, request.getParameter("note"))));
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Stock stock = new Stock(this.database);
        final Stock.Form form = Stock.Form.of(field(request, "form"));
        final String note;
        try {
            if (form == null) {
                throw new IllegalArgumentException("Выберите форму бланка");
            }
            if ("receive".equals(field(request, "action"))) {
                final int count = stock.receive(form, field(request, "series"), field(request, "numbers"),
                    field(request, "received"), field(request, "note"));
                note = "Принято бланков «" + form.title() + "»: " + count;
            } else if ("spoil".equals(field(request, "action"))) {
                stock.spoil(form, field(request, "series"), field(request, "number"), field(request, "reason"));
                note = "Бланк " + field(request, "series") + " " + field(request, "number") + " списан как испорченный";
            } else {
                throw new IllegalArgumentException("Действие не известно");
            }
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить бланки", error);
        }
        response.sendRedirect(request.getContextPath() + "/stock?note=" + URLEncoder.encode(note, StandardCharsets.UTF_8));
    }

    private static String page(final Stock stock, final String note) throws SQLException {
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1>");
        if (note != null) {
            body.append("<section class=\"note\">").append(Html.escape(note)).append("</section>");
        }
        body.append("<section><p class=\"muted\">Бланки дипломов и приложений — документы строгой отчётности ")
            .append("(п. 47 приказа № 670): здесь учитываются приход от типографии, бланки, на которых напечатаны ")
            .append("документы, и испорченные. Испорченные бланки уничтожаются в порядке, принятом в вузе (п. 5).</p>")
            .append("<p><a href=\"stock?format=xlsx\">Реестр бланков (XLSX)</a> · <a href=\"book\">Книга регистрации</a>")
            .append("</p><table><tr><th>Форма</th><th>Свободно</th><th>Использовано</th><th>Испорчено</th></tr>");
        final List<Stock.Count> counts = stock.counts();
        for (final Stock.Count count : counts) {
            body.append("<tr><td>").append(Html.escape(count.form.title())).append("</td><td>").append(count.free)
                .append("</td><td>").append(count.used).append("</td><td>").append(count.spoiled).append("</td></tr>");
        }
        if (counts.isEmpty()) {
            body.append("<tr><td colspan=\"4\" class=\"muted\">Бланков в учёте пока нет.</td></tr>");
        }
        body.append("</table></section>");
        body.append("<section><h2>Приход</h2><form method=\"post\" action=\"stock\">")
            .append("<input type=\"hidden\" name=\"action\" value=\"receive\">").append(forms())
            .append("<label>Серия — шесть цифр</label><input type=\"text\" name=\"series\" required>")
            .append("<label>Номера — по семь цифр, подряд или с пропусками: «0000101–0000150, 0000160»</label>")
            .append("<input type=\"text\" name=\"numbers\" required>")
            .append("<label>Дата получения</label><input type=\"date\" name=\"received\" required>")
            .append("<label>Примечание: накладная, от кого получены</label><input type=\"text\" name=\"note\">")
            .append("<br><button>Внести приход</button></form></section>");
        body.append("<section><h2>Испорченный бланк</h2><p class=\"muted\">Бланк, испорченный не при печати ")
            .append("документа, — например, при пробной печати или при хранении. Бланк, на котором напечатан ")
            .append("документ, отмечается в карточке выпускника.</p><form method=\"post\" action=\"stock\">")
            .append("<input type=\"hidden\" name=\"action\" value=\"spoil\">").append(forms())
            .append("<label>Серия</label><input type=\"text\" name=\"series\" required>")
            .append("<label>Номер</label><input type=\"text\" name=\"number\" required>")
            .append("<label>Почему испорчен</label><input type=\"text\" name=\"reason\" required>")
            .append("<br><button>Списать бланк</button></form></section>");
        body.append("<section><h2>Партии</h2>");
        final List<Stock.Batch> batches = stock.batches();
        if (batches.isEmpty()) {
            body.append("<p class=\"muted\">Пока нет.</p>");
        } else {
            body.append("<table><tr><th>Получены</th><th>Форма</th><th>Серия</th><th>Номера</th><th>Штук</th>")
                .append("<th>Примечание</th></tr>");
            for (final Stock.Batch batch : batches) {
                body.append("<tr><td>").append(Html.escape(Issuing.date(batch.received))).append("</td><td>")
                    .append(Html.escape(batch.form.title())).append("</td><td>").append(Html.escape(batch.series))
                    .append("</td><td>").append(Html.escape(batch.numbers)).append("</td><td>").append(batch.quantity)
                    .append("</td><td>").append(Html.escape(batch.note)).append("</td></tr>");
            }
            body.append("</table>");
        }
        body.append("</section><section><h2>Испорченные</h2>");
        final List<Stock.Blank> spoiled = stock.spoiled();
        if (spoiled.isEmpty()) {
            body.append("<p class=\"muted\">Нет.</p>");
        } else {
            body.append("<table><tr><th>Форма</th><th>Серия и номер</th><th>Документ</th><th>Причина</th><th>Когда</th></tr>");
            for (final Stock.Blank blank : spoiled) {
                body.append("<tr><td>").append(Html.escape(blank.form.title())).append("</td><td>")
                    .append(Html.escape(blank.label())).append("</td><td>")
                    .append(blank.regNumber.isEmpty() ? "—" : "№ " + Html.escape(blank.regNumber)).append("</td><td>")
                    .append(Html.escape(blank.reason)).append("</td><td>").append(Html.escape(PlanView.time(blank.changedAt)))
                    .append("</td></tr>");
            }
            body.append("</table>");
        }
        body.append("</section><section><h2>Журнал</h2><p class=\"muted\">Приход и бланки, испорченные не при ")
            .append("печати документа; записи бланков документам — в журналах выпусков.</p><ul>");
        for (final String entry : stock.journal()) {
            body.append("<li>").append(Html.escape(entry)).append("</li>");
        }
        return body.append("</ul></section>").toString();
    }

    private static String forms() {
        final StringBuilder html = new StringBuilder("<label>Форма бланка</label><select name=\"form\">");
        for (final Stock.Form form : Stock.Form.all()) {
            html.append("<option value=\"").append(form.code()).append("\">").append(Html.escape(form.title()))
                .append("</option>");
        }
        return html.append("</select>").toString();
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
