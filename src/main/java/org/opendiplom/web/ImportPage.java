package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.opendiplom.imports.CreditCheck;
import org.opendiplom.imports.Curriculum;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.ImportBatches;

/**
 * Reads a statement and, optionally, the curriculum into the staging zone and
 * shows what was read and what the operator must check.
 */
final class ImportPage extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final transient Database database;

    ImportPage(final Database database) {
        this.database = database;
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Uploads uploads = new Uploads(request);
        final byte[] content = uploads.file("statement");
        if (content == null) {
            Responses.problem(response, "Импорт ведомости", "Выберите файл ведомости");
            return;
        }
        final StatementImport statement;
        try {
            statement = StatementImport.read(content);
        } catch (final WorkbookException error) {
            Responses.problem(response, "Импорт ведомости", error.getMessage());
            return;
        }
        final byte[] plan = uploads.file("curriculum");
        Curriculum curriculum = null;
        String planProblem = "учебный план не загружен";
        if (plan != null) {
            try {
                curriculum = Curriculum.read(plan);
            } catch (final WorkbookException error) {
                planProblem = "учебный план не прочитан (" + error.getMessage() + ")";
            }
        }
        final List<CreditCheck> checks = CreditCheck.settle(statement, curriculum, planProblem);
        try {
            new ImportBatches(this.database).save(
                uploads.fileName("statement"), uploads.fileName("curriculum"), statement, checks
            );
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить импорт", error);
        }
        Responses.html(
            response, HttpServletResponse.SC_OK,
            Html.page("Импорт ведомости", report(statement, curriculum, checks))
        );
    }

    private static String report(
        final StatementImport statement, final Curriculum curriculum, final List<CreditCheck> checks
    ) {
        final StringBuilder body = new StringBuilder("<h1>Ведомость прочитана</h1>");
        body.append("<p>Студентов: ").append(statement.students().size())
            .append(", предметов: ").append(statement.labels().size());
        if (curriculum != null) {
            body.append(". З.е. из учебного плана, лист «").append(Html.escape(curriculum.sheet()))
                .append("»");
        }
        body.append(". Импорт сохранён в промежуточной зоне.</p>");
        body.append("<h2>Зачётные единицы</h2><div class=\"scroll\"><table><tr><th>Предмет</th>")
            .append("<th>По часам</th><th>По плану</th><th>Итог</th><th>Источник</th><th>Проверить</th></tr>");
        for (final CreditCheck check : checks) {
            body.append("<tr><td>").append(Html.escape(check.label()))
                .append("</td><td>").append(check.counted())
                .append("</td><td>").append(check.planned() == null ? "—" : Html.escape(Cells.text(check.planned())))
                .append("</td><td>").append(Html.escape(check.settled()))
                .append("</td><td>").append(Html.escape(check.source()))
                .append("</td><td class=\"note\">").append(Html.escape(check.notes()))
                .append("</td></tr>");
        }
        body.append("</table></div><h2>Оценки</h2><div class=\"scroll\"><table><tr><th>Предмет</th>");
        for (final String student : statement.students()) {
            body.append("<th>").append(Html.escape(student)).append("</th>");
        }
        body.append("</tr>");
        for (final String label : statement.labels()) {
            body.append("<tr><td>").append(Html.escape(label)).append("</td>");
            for (final String student : statement.students()) {
                final Object grade = statement.grade(student, label);
                body.append("<td>").append(grade == null ? "" : Html.escape(Cells.text(grade))).append("</td>");
            }
            body.append("</tr>");
        }
        return body.append("</table></div>").toString();
    }
}
