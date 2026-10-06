package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.storage.Blanks;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Documents;
import org.opendiplom.storage.Graduations;
import org.opendiplom.storage.Stock;

/**
 * The book of registration of the documents issued (Order No 670, item 49):
 * a row a document with a registration number, by the number, over a period
 * of the dates of issue; as a page and as XLSX to print. The book itself is
 * paper — its sheets numbered, laced and sealed (item 50) — and the
 * signatures go there.
 */
final class BookPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Книга регистрации";
    /** The columns item 49 lists. */
    static final String[] COLUMNS = {
        "Регистрационный номер", "Фамилия, имя, отчество", "Серия и номер бланка диплома",
        "Серия и номер бланка (бланков) приложения", "Дата выдачи",
        "Код и наименование специальности, направления подготовки", "Квалификация; дата и номер протокола ГЭК",
        "Подпись руководителя подразделения", "Подпись получателя; доверенность или почтовое отправление",
    };
    private static final int[] WIDTHS = {13, 28, 18, 18, 11, 34, 26, 16, 30};

    private final transient Database database;

    BookPage(final Database database) {
        this.database = database;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final int year = LocalDate.now().getYear();
        final String from = field(request, "from", year + "-01-01");
        final String to = field(request, "to", year + "-12-31");
        if (!from.matches("\\d{4}-\\d{2}-\\d{2}") || !to.matches("\\d{4}-\\d{2}-\\d{2}")) {
            Responses.problem(response, TITLE, "Период: нужны даты ГГГГ-ММ-ДД");
            return;
        }
        final List<String[]> rows;
        final String organization;
        try {
            rows = rows(this.database, from, to);
            organization = new Documents(this.database).organization().fullName.replace('\n', ' ').strip();
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
        final String period = "с " + Issuing.date(from) + " по " + Issuing.date(to);
        if ("xlsx".equals(request.getParameter("format"))) {
            Responses.file(response, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Книга регистрации " + from + " — " + to + ".xlsx",
                Spreadsheet.of(TITLE, "Книга регистрации выданных документов об образовании и о квалификации"
                    + (organization.isEmpty() ? "" : ", " + organization) + "; даты выдачи " + period, COLUMNS, WIDTHS,
                    rows));
            return;
        }
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1><section><p class=\"muted\">")
            .append("Документы с регистрационным номером и датой выдачи за период, по номеру (п. 49 приказа № 670). ")
            .append("Книга ведётся на бумаге: листы нумеруются, прошнуровываются и скрепляются печатью (п. 50), ")
            .append("подписи ставятся в ней.</p><form method=\"get\" action=\"book\"><label>Даты выдачи с</label>")
            .append("<input type=\"date\" name=\"from\" value=\"").append(Html.escape(from)).append("\"><label>по</label>")
            .append("<input type=\"date\" name=\"to\" value=\"").append(Html.escape(to)).append("\"> <button>Показать</button>")
            .append("</form><p><a href=\"book?from=").append(Html.escape(from)).append("&amp;to=").append(Html.escape(to))
            .append("&amp;format=xlsx\">Скачать для печати (XLSX)</a> · <a href=\"stock\">Учёт бланков</a></p></section>")
            .append("<section><h2>Документов: ").append(rows.size()).append("</h2>");
        if (!rows.isEmpty()) {
            body.append("<div class=\"scroll\"><table><tr>");
            for (final String column : COLUMNS) {
                body.append("<th>").append(Html.escape(column)).append("</th>");
            }
            body.append("</tr>");
            for (final String[] row : rows) {
                body.append("<tr>");
                for (final String cell : row) {
                    body.append("<td>").append(Html.escape(cell)).append("</td>");
                }
                body.append("</tr>");
            }
            body.append("</table></div>");
        }
        body.append("</section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /**
     * The rows of the book: the documents of the registered graduations
     * issued in the period, the graduates as corrected (ADR-0012).
     */
    static List<String[]> rows(final Database database, final String from, final String to) throws SQLException {
        final List<String[]> rows = new ArrayList<>();
        for (final Graduations.Graduation graduation : new Graduations(database).all()) {
            if (graduation.staging()) {
                continue;
            }
            final Registered registered = Registered.load(database, graduation.id);
            if (registered == null || registered.edition == null) {
                continue;
            }
            for (int number = 0; number < registered.graduates.size(); ++number) {
                final GraduateRecord graduate = registered.graduates.get(number);
                for (final DocumentRecord document : registered.documents.getOrDefault(graduate.id, List.of())) {
                    if (document.regNumber.isEmpty() || document.issueDate.isEmpty()
                        || document.issueDate.compareTo(from) < 0 || document.issueDate.compareTo(to) > 0) {
                        continue;
                    }
                    final Stock.Issue issue = registered.issues.get(document.id);
                    rows.add(new String[] {
                        document.regNumber, graduate.fullName(), blank(registered, document, Blanks.DIPLOMA),
                        blank(registered, document, Blanks.SUPPLEMENT), Issuing.date(document.issueDate),
                        registered.fields.getOrDefault("direction", ""),
                        registered.fields.getOrDefault("qualification", "") + "; " + Issuing.date(graduate.gekDate)
                            + ", протокол № " + graduate.gekProtocol,
                        "",
                        issue == null ? "" : issue.text() + ", " + Issuing.date(issue.handed),
                    });
                }
            }
        }
        rows.sort(Comparator.comparing((String[] row) -> row[0].length()).thenComparing(row -> row[0]));
        return rows;
    }

    /** «123456 0000101», «дубликат: 123456 0000301»; «—» for a duplicate without the kind. */
    private static String blank(final Registered registered, final DocumentRecord document, final String kind) {
        if (!Issuing.kinds(document).contains(kind)) {
            return "—";
        }
        final Stock.Blank used = registered.used(document.id, kind);
        final String label = used == null ? "" : used.label();
        return document.duplicate() ? "дубликат: " + label : label;
    }

    private static String field(final HttpServletRequest request, final String name, final String fallback) {
        final String value = request.getParameter(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
