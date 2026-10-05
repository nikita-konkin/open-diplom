package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.Settings;
import org.opendiplom.catalog.Organization;
import org.opendiplom.printing.BlankData;
import org.opendiplom.printing.BlankPrint;
import org.opendiplom.printing.BlankTemplate;
import org.opendiplom.printing.Calibration;
import org.opendiplom.printing.Fonts;
import org.opendiplom.storage.Blanks;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Documents;

/**
 * The templates of the blanks and the printer (ADR-0011). The operator
 * uploads the FastReport template of each document and level that came with
 * the blanks, sees what it asks on made-up data, and keeps the shift of the
 * printer measured on the test sheet.
 */
final class BlanksPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Бланки и принтер";
    private static final Pattern ROUTE = Pattern.compile(
        "^/blanks(?:/(upload|delete|calibration)|/(diploma|supplement)-(\\d{2})\\.(pdf|fr3))?/?$"
    );

    private final transient Database database;
    private final transient Settings settings;

    BlanksPage(final Database database, final Settings settings) {
        this.database = database;
        this.settings = settings;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Matcher route = ROUTE.matcher(path(request));
        try {
            if (!route.matches() || route.group(1) != null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if (route.group(2) == null) {
                this.page(request, response);
            } else if ("fr3".equals(route.group(4))) {
                this.download(route.group(2), route.group(3), response);
            } else {
                this.sample(route.group(2), route.group(3), response);
            }
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Matcher route = ROUTE.matcher(path(request));
        if (!route.matches() || route.group(1) == null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        final Uploads uploads = new Uploads(request);
        final Blanks blanks = new Blanks(this.database);
        final String note;
        try {
            if ("upload".equals(route.group(1))) {
                final byte[] content = uploads.file("template");
                if (content == null) {
                    Responses.problem(response, TITLE, "Выберите файл шаблона .fr3");
                    return;
                }
                blanks.save(uploads.field("kind"), uploads.field("level"), name(uploads.fileName("template")), content);
                note = "Шаблон загружен: " + Blanks.KINDS.get(uploads.field("kind")) + ", "
                    + Blanks.LEVELS.get(uploads.field("level"));
            } else if ("delete".equals(route.group(1))) {
                blanks.delete(uploads.field("kind"), uploads.field("level"));
                note = "Шаблон удалён";
            } else {
                final Calibration calibration = new Calibration(
                    millimetres(uploads.field("dx")), millimetres(uploads.field("dy"))
                );
                blanks.calibration(calibration);
                note = String.format(Locale.ROOT, "Поправка принтера сохранена: вправо %.1f мм, вниз %.1f мм",
                    calibration.dx(), calibration.dy());
            }
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить", error);
        }
        response.sendRedirect(request.getContextPath() + "/blanks?note="
            + URLEncoder.encode(note, StandardCharsets.UTF_8));
    }

    private void page(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Blanks blanks = new Blanks(this.database);
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1>");
        final String note = request.getParameter("note");
        if (note != null) {
            body.append("<section class=\"note\">").append(Html.escape(note)).append("</section>");
        }
        body.append("<section><h2>Шаблоны</h2><p>Шаблоны FastReport (.fr3) приходят от типографии вместе с ")
            .append("бланками, как для КиберДиплома. В открытом репозитории их нет: у каждого вуза свои. ")
            .append("Шаблон выбирается по документу и уровню образования выпуска. Образец печатает ")
            .append("вымышленного выпускника с отметкой «ОБРАЗЕЦ» и списком замечаний на последней странице.</p>")
            .append("<table><tr><th>Документ</th><th>Уровень</th><th>Файл</th><th>Проверка</th><th></th></tr>");
        final Organization organization = new Documents(this.database).organization();
        for (final String kind : Blanks.KINDS.keySet()) {
            for (final String level : Blanks.LEVELS.keySet()) {
                final Blanks.Template template = blanks.find(kind, level);
                body.append("<tr><td>").append(Html.escape(Blanks.KINDS.get(kind))).append("</td><td>")
                    .append(Html.escape(Blanks.LEVELS.get(level))).append("</td>");
                if (template == null) {
                    body.append("<td class=\"muted\">не загружен</td><td></td><td></td></tr>");
                    continue;
                }
                body.append("<td><a href=\"blanks/").append(kind).append('-').append(level).append(".fr3\">")
                    .append(Html.escape(template.fileName)).append("</a><br><span class=\"muted\">")
                    .append(Html.escape(PlanView.time(template.uploadedAt))).append("</span></td><td>")
                    .append(this.check(template, level, organization)).append("</td><td><a href=\"blanks/")
                    .append(kind).append('-').append(level).append(".pdf\">Образец (PDF)</a>")
                    .append("<form method=\"post\" action=\"blanks/delete\" enctype=\"multipart/form-data\">")
                    .append(hidden(kind, level)).append("<button>Удалить</button></form></td></tr>");
            }
        }
        body.append("</table><form method=\"post\" action=\"blanks/upload\" enctype=\"multipart/form-data\">")
            .append("<label>Документ</label><select name=\"kind\">");
        for (final String kind : Blanks.KINDS.keySet()) {
            body.append("<option value=\"").append(kind).append("\">").append(Html.escape(Blanks.KINDS.get(kind)))
                .append("</option>");
        }
        body.append("</select><label>Уровень образования</label><select name=\"level\">");
        for (final String level : Blanks.LEVELS.keySet()) {
            body.append("<option value=\"").append(level).append("\">").append(Html.escape(Blanks.LEVELS.get(level)))
                .append("</option>");
        }
        final Calibration calibration = blanks.calibration();
        body.append("</select><label>Файл шаблона (.fr3)</label><input type=\"file\" name=\"template\" accept=\".fr3\">")
            .append("<br><button>Загрузить</button></form></section>")
            .append("<section><h2>Принтер</h2><p>Напечатайте <a href=\"test-sheet.pdf?dx=")
            .append(number(calibration.dx())).append("&amp;dy=").append(number(calibration.dy()))
            .append("\">тестовый лист</a> в масштабе 100%, измерьте расстояние от краёв бумаги до центра ")
            .append("левого верхнего креста и впишите поправку: 20 минус измеренное. Поправка добавляется ко ")
            .append("всем полям бланков.</p><form method=\"post\" action=\"blanks/calibration\" ")
            .append("enctype=\"multipart/form-data\"><label>Вправо, мм</label><input type=\"text\" name=\"dx\" value=\"")
            .append(number(calibration.dx())).append("\"><label>Вниз, мм</label><input type=\"text\" name=\"dy\" value=\"")
            .append(number(calibration.dy())).append("\"><br><button>Сохранить поправку</button></form></section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /** What the template asks that the made-up data cannot give, or what does not fit. */
    private String check(final Blanks.Template stored, final String level, final Organization organization)
        throws IOException {
        final BlankTemplate template = BlankTemplate.of(stored.content);
        final Optional<Path> font = Fonts.serif(this.settings.font());
        final StringBuilder html = new StringBuilder();
        html.append(template.sheets.size()).append(template.sheets.size() == 1 ? " страница" : " страницы");
        if (font.isEmpty()) {
            return html.append("; не найден шрифт для проверки").toString();
        }
        final Set<String> problems = new LinkedHashSet<>();
        BlankPrint.pdf(
            template, List.of(this.sample(template, level, organization)), font.get(), null, Calibration.NONE, false,
            problems
        );
        if (problems.isEmpty()) {
            return html.append("; замечаний нет").toString();
        }
        html.append("<ul>");
        for (final String problem : problems) {
            html.append("<li class=\"note\">").append(Html.escape(problem)).append("</li>");
        }
        return html.append("</ul>").toString();
    }

    private BlankData sample(final BlankTemplate template, final String level, final Organization organization) {
        return BlankData.sample(level, organization, template.datasets().contains(BlankData.COURSE_WORKS));
    }

    private void sample(final String kind, final String level, final HttpServletResponse response)
        throws IOException, SQLException {
        final Blanks blanks = new Blanks(this.database);
        final Blanks.Template stored = blanks.find(kind, level);
        if (stored == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final Optional<Path> font = Fonts.serif(this.settings.font());
        if (font.isEmpty()) {
            Responses.problem(response, TITLE, "Не найден шрифт с кириллицей (PT Astra Serif, Times New Roman или "
                + "Liberation Serif). Установите один из них или укажите файл шрифта в настройке font.");
            return;
        }
        final BlankTemplate template = BlankTemplate.of(stored.content);
        final byte[] pdf = BlankPrint.pdf(
            template, List.of(this.sample(template, level, new Documents(this.database).organization())), font.get(),
            Fonts.bold(font.get()).orElse(null), blanks.calibration(), true, new LinkedHashSet<>()
        );
        Responses.pdf(response, "Образец " + Blanks.KINDS.get(kind) + " " + Blanks.LEVELS.get(level) + ".pdf", pdf);
    }

    private void download(final String kind, final String level, final HttpServletResponse response)
        throws IOException, SQLException {
        final Blanks.Template stored = new Blanks(this.database).find(kind, level);
        if (stored == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Responses.file(response, "application/octet-stream", stored.fileName, stored.content);
    }

    private static String hidden(final String kind, final String level) {
        return "<input type=\"hidden\" name=\"kind\" value=\"" + kind + "\"><input type=\"hidden\" name=\"level\" value=\""
            + level + "\">";
    }

    private static String number(final float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static float millimetres(final String value) {
        if (value.isBlank()) {
            return 0;
        }
        try {
            return Float.parseFloat(value.strip().replace(',', '.'));
        } catch (final NumberFormatException error) {
            throw new IllegalArgumentException("Поправка «" + value + "» — не число миллиметров", error);
        }
    }

    private static String name(final String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "шаблон.fr3";
        }
        final String name = fileName.replace('\\', '/');
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private static String path(final HttpServletRequest request) {
        return request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    }
}
