package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import org.opendiplom.catalog.Organization;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Documents;

/**
 * The organization as its diplomas print it (ADR-0010): the full name in its
 * printed lines, the locality after it (D-03) and the head.
 */
final class OrganizationPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Данные вуза";

    private final transient Database database;

    OrganizationPage(final Database database) {
        this.database = database;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Organization organization;
        try {
            organization = new Documents(this.database).organization();
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1>");
        if (request.getParameter("saved") != null) {
            body.append("<section class=\"note\">Сохранено.</section>");
        }
        if (!organization.missing().isEmpty()) {
            body.append("<section class=\"note\">Без этого документы не напечатать: ")
                .append(Html.escape(String.join(", ", organization.missing()))).append(".</section>");
        }
        body.append("<section><form method=\"post\" action=\"organization\">")
            .append("<label>Полное наименование — по строкам, как на бланке</label>")
            .append("<textarea name=\"full_name\" rows=\"6\" cols=\"60\">").append(Html.escape(organization.fullName))
            .append("</textarea>")
            .append(input("Населённый пункт, печатается после наименования", "locality", organization.locality,
                "г. Йошкар-Ола"))
            .append(input("Руководитель: фамилия", "head_last_name", organization.headLastName, ""))
            .append(input("Руководитель: имя", "head_first_name", organization.headFirstName, ""))
            .append(input("Руководитель: отчество", "head_middle_name", organization.headMiddleName, ""))
            .append("<p class=\"muted\">Подпись руководителя печатается как «")
            .append(Html.escape(organization.head().isEmpty() ? "И.О. Фамилия" : organization.head()))
            .append("».</p><button>Сохранить</button></form></section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final String name = field(request, "full_name").replace("\r\n", "\n");
        try {
            new Documents(this.database).organization(new Organization(
                name, field(request, "locality"), field(request, "head_last_name"), field(request, "head_first_name"),
                field(request, "head_middle_name")
            ));
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить данные вуза", error);
        }
        response.sendRedirect(request.getContextPath() + "/organization?saved=1");
    }

    private static String input(final String label, final String name, final String value, final String placeholder) {
        return "<label>" + Html.escape(label) + "</label><input type=\"text\" name=\"" + name + "\" value=\""
            + Html.escape(value) + "\" placeholder=\"" + Html.escape(placeholder) + "\">";
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
