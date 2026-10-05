package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.Settings;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.printing.BlankData;
import org.opendiplom.printing.BlankPrint;
import org.opendiplom.printing.BlankTemplate;
import org.opendiplom.printing.Fonts;
import org.opendiplom.storage.Blanks;
import org.opendiplom.storage.Database;

/**
 * Diplomas and supplements of a graduation as PDF for the blanks (ADR-0011):
 * of one graduate or of all, from the template uploaded for the level.
 *
 * <p>A document prints only when its check for printing has neither errors
 * nor unfinished points, and when the layout found no fault — a field the
 * data has not, a text that does not fit, a table that took another page —
 * unless the operator accepts the faults. A sample prints whatever is there,
 * marked «ОБРАЗЕЦ», with the faults on a last page.
 */
final class PrintPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Печать";
    private static final Pattern ROUTE = Pattern.compile("^/print/([0-9a-f-]{36})/(diploma|supplement)\\.pdf$");

    private final transient Database database;
    private final transient Settings settings;

    PrintPage(final Database database, final Settings settings) {
        this.database = database;
        this.settings = settings;
    }

    /** The address of a PDF, relative to the root of the pages. */
    static String link(
        final String graduation, final String kind, final String graduate, final String document, final boolean sample
    ) {
        final List<String> query = new ArrayList<>();
        if (graduate != null) {
            query.add("graduate=" + graduate);
        }
        if (document != null) {
            query.add("document=" + document);
        }
        if (sample) {
            query.add("sample=1");
        }
        return "print/" + graduation + "/" + kind + ".pdf" + (query.isEmpty() ? "" : "?" + String.join("&amp;", query));
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final String path = request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
        final Matcher route = ROUTE.matcher(path);
        if (!route.matches()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            this.print(route.group(1), route.group(2), request, response);
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
    }

    private void print(
        final String id, final String kind, final HttpServletRequest request, final HttpServletResponse response
    ) throws IOException, SQLException {
        final Registered registered = Registered.load(this.database, id);
        if (registered == null || registered.graduation.staging()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final boolean sample = "1".equals(request.getParameter("sample"));
        if (registered.program == null) {
            Responses.problem(response, TITLE, "Программа выпуска не заполнена:\n"
                + String.join("\n", registered.programProblems));
            return;
        }
        final String level = Blanks.level(registered.program.directionCode());
        final Blanks blanks = new Blanks(this.database);
        final Blanks.Template stored = blanks.find(kind, level);
        if (stored == null) {
            Responses.problem(response, TITLE, "Нет шаблона «" + Blanks.KINDS.get(kind) + "» для уровня «"
                + Blanks.LEVELS.getOrDefault(level, level) + "»: загрузите его на странице «Бланки и принтер».");
            return;
        }
        final Optional<Path> font = Fonts.serif(this.settings.font());
        if (font.isEmpty()) {
            Responses.problem(response, TITLE, "Не найден шрифт с кириллицей (PT Astra Serif, Times New Roman или "
                + "Liberation Serif). Установите один из них или укажите файл шрифта в настройке font.");
            return;
        }
        final BlankTemplate template = BlankTemplate.of(stored.content);
        final boolean apart = template.datasets().contains(BlankData.COURSE_WORKS);
        final String only = request.getParameter("graduate");
        final List<BlankData> documents = new ArrayList<>();
        final List<String> refused = new ArrayList<>();
        String name = registered.graduation.groupName;
        for (int number = 0; number < registered.graduates.size(); ++number) {
            final GraduateRecord graduate = registered.graduates.get(number);
            if (only != null && !only.equals(graduate.id)) {
                continue;
            }
            final DocumentRecord document = this.document(registered, number, request.getParameter("document"));
            if (document == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            final List<Checks.Finding> findings = document == registered.originals.get(number)
                ? registered.printing.get(number)
                : Checks.printing(registered.findings.get(number), graduate, document, registered.honors.get(number),
                    registered.organization);
            for (final Checks.Finding finding : findings) {
                if (finding.level != Checks.Level.WARNING) {
                    refused.add(graduate.fullName() + ": " + finding.message);
                }
            }
            documents.add(BlankData.of(
                registered.program, graduate, document, document.honors(registered.honors.get(number)),
                registered.organization, Checks.stateExam(registered.plan), apart
            ));
            if (only != null) {
                name += " " + graduate.lastName;
            }
        }
        if (documents.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (!sample && !refused.isEmpty()) {
            Responses.problem(response, TITLE, "Не напечатано: документы не готовы. Образец можно открыть всегда.\n"
                + String.join("\n", refused));
            return;
        }
        final Set<String> problems = new LinkedHashSet<>();
        final byte[] pdf = BlankPrint.pdf(
            template, documents, font.get(), Fonts.bold(font.get()).orElse(null), blanks.calibration(), sample, problems
        );
        if (!sample && !problems.isEmpty() && !"1".equals(request.getParameter("accept"))) {
            final String query = request.getQueryString() == null ? "" : request.getQueryString() + "&";
            final StringBuilder list = new StringBuilder();
            for (final String problem : problems) {
                list.append("<li>").append(Html.escape(problem)).append("</li>");
            }
            Responses.html(response, HttpServletResponse.SC_CONFLICT, Html.page(TITLE, "<h1>" + TITLE
                + "</h1><section class=\"note\"><p>Раскладка на бланке с замечаниями:</p><ul>" + list
                + "</ul><p><a href=\"" + Html.escape(path(id, kind) + "?" + query + "sample=1")
                + "\">Открыть образец</a> · <a href=\"" + Html.escape(path(id, kind) + "?" + query + "accept=1")
                + "\">Напечатать всё равно</a></p></section>"));
            return;
        }
        Responses.pdf(response, (sample ? "Образец " : "") + Blanks.KINDS.get(kind) + " " + name + ".pdf", pdf);
    }

    private static String path(final String id, final String kind) {
        return "print/" + id + "/" + kind + ".pdf";
    }

    /** The document asked for, the original by default; {@code null} when the graduate has no such. */
    private DocumentRecord document(final Registered registered, final int number, final String id) {
        final DocumentRecord original = registered.originals.get(number);
        if (id == null || id.equals(original.id)) {
            return original;
        }
        for (final DocumentRecord document : registered.documents.getOrDefault(
            registered.graduates.get(number).id, List.of()
        )) {
            if (id.equals(document.id)) {
                return document;
            }
        }
        return null;
    }
}
