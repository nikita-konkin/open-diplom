package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.opendiplom.imports.Curriculum;
import org.opendiplom.sheets.WorkbookException;

/**
 * Fills the fields of the XML form from a curriculum: the title gives the
 * direction, profile, qualification, form and term, the totals give the
 * volumes. The operator checks them before making the XML.
 */
final class XmlPlanPage extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Uploads uploads = new Uploads(request);
        final byte[] content = uploads.file("curriculum");
        if (content == null) {
            Responses.problem(response, XmlPage.TITLE, "Выберите файл учебного плана");
            return;
        }
        final Curriculum plan;
        try {
            plan = Curriculum.read(content);
        } catch (final WorkbookException error) {
            Responses.problem(response, XmlPage.TITLE, error.getMessage());
            return;
        }
        final Map<String, String> sources = new HashMap<>();
        final Map<String, String> values = XmlPage.fromPlan(plan, sources);
        final StringBuilder body = new StringBuilder("<h1>").append(XmlPage.TITLE).append("</h1>")
            .append("<p>Поля заполнены из учебного плана «").append(Html.escape(uploads.fileName("curriculum")))
            .append("» (").append(Html.escape(plan.origin())).append("). Проверьте их, впишите председателя ГЭК, ")
            .append("выберите сводную таблицу и сведения о студентах.</p>");
        if (!plan.totals().problems().isEmpty()) {
            body.append("<section class=\"error\">")
                .append(Html.escape(String.join("\n", plan.totals().problems()))).append("</section>");
        }
        body.append(XmlPage.form(values, sources)).append("<p><a href=\"./\">На главную</a></p>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(XmlPage.TITLE, body.toString()));
    }
}
