package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.imports.Curriculum;
import org.opendiplom.plans.PlanCheck;
import org.opendiplom.plans.PlanDiff;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.storage.Curricula;
import org.opendiplom.storage.Database;

/**
 * Curricula by editions (ADR-0008): the list, loading a file through a
 * preview, an edition with its sums and changes, correcting it and making the
 * plan of another year or form from it.
 *
 * <p>A loaded file waits in the staging zone (ADR-0004) until the operator
 * confirms its title; then it is saved and the file deleted.
 */
final class PlansPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Учебные планы";
    private static final Pattern ROUTE = Pattern.compile(
        "^/plans(?:/(upload|save)|/([0-9a-f-]{36})(?:/(edit|derive))?)?/?$"
    );
    private static final Pattern ID = Pattern.compile("[0-9a-f-]{36}");
    private static final String CONTENT = "plan.bin";
    private static final String NAME = "name.txt";
    /** A file loaded and not saved is deleted after this long. */
    private static final Duration STALE = Duration.ofDays(1);
    /** Contact and self-study hours differ between study forms; the rest follows the credits. */
    private static final int FORM_HOURS = PlanRow.CONTACT;

    private final transient Database database;
    private final transient Path staging;

    PlansPage(final Database database, final Path staging) {
        this.database = database;
        this.staging = staging;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Matcher route = ROUTE.matcher(path(request));
        try {
            if (!route.matches() || route.group(1) != null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if (route.group(2) == null) {
                this.list(response);
            } else if (route.group(3) == null) {
                this.edition(route.group(2), request, response);
            } else {
                this.form(route.group(2), "derive".equals(route.group(3)), request, response);
            }
        } catch (final SQLException error) {
            throw new ServletException("База данных недоступна", error);
        }
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Matcher route = ROUTE.matcher(path(request));
        try {
            if (!route.matches()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if ("upload".equals(route.group(1))) {
                this.upload(request, response);
            } else if ("save".equals(route.group(1))) {
                this.save(request, response);
            } else if ("edit".equals(route.group(3))) {
                this.edited(route.group(2), request, response);
            } else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить учебный план", error);
        }
    }

    /** All editions and the form to load a plan. */
    private void list(final HttpServletResponse response) throws IOException, SQLException {
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1>")
            .append("<section><h2>Загрузить план</h2><p>PDF, сохранённый из «Планов», или Excel. Скан не ")
            .append("читается: откройте план этой программы за другой год или другой формы и нажмите ")
            .append("«Создать на основе».</p><form method=\"post\" action=\"plans/upload\" ")
            .append("enctype=\"multipart/form-data\"><input type=\"file\" name=\"plan\" accept=\".pdf,.xls,.xlsx\" ")
            .append("required><br><button>Прочитать</button></form></section>");
        final List<Curricula.Edition> editions = new Curricula(this.database).all();
        body.append("<section><h2>Загруженные планы</h2>");
        if (editions.isEmpty()) {
            body.append("<p class=\"muted\">Пока нет ни одного.</p>");
        } else {
            body.append("<table><tr><th>Направление и профиль</th><th>Форма</th><th>Год набора</th>")
                .append("<th>Редакция</th><th>Источник</th><th>Ошибок в суммах</th><th>Сохранено</th></tr>");
            for (final Curricula.Edition edition : editions) {
                body.append("<tr><td>").append(Html.escape(edition.header.code() + " " + edition.header.profile()))
                    .append("</td><td>").append(Html.escape(edition.header.studyForm()))
                    .append("</td><td>").append(Html.escape(edition.header.year()))
                    .append("</td><td><a href=\"plans/").append(edition.id).append("\">").append(edition.edition)
                    .append("</a></td><td>").append(Html.escape(source(edition)))
                    .append("</td><td").append(edition.errors > 0 ? " class=\"error\"" : "").append(">")
                    .append(edition.errors).append("</td><td>").append(Html.escape(PlanView.time(edition.createdAt)))
                    .append("</td></tr>");
            }
            body.append("</table>");
        }
        body.append("</section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /** Reads a file into the staging zone and shows it before saving. */
    private void upload(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException, SQLException {
        final Uploads uploads = new Uploads(request);
        final byte[] content = uploads.file("plan");
        if (content == null) {
            this.problem(response, "Выберите файл учебного плана");
            return;
        }
        final Curriculum plan;
        try {
            plan = Curriculum.read(content);
        } catch (final WorkbookException error) {
            this.problem(response, error.getMessage());
            return;
        }
        this.sweep();
        final String id = UUID.randomUUID().toString();
        final Path folder = Files.createDirectories(this.staging.resolve(id));
        Files.write(folder.resolve(CONTENT), content);
        final String name = uploads.fileName("plan");
        Files.write(folder.resolve(NAME), (name == null ? "" : name).getBytes(StandardCharsets.UTF_8));
        this.preview(response, id, name, plan, PlanHeader.of(plan.title()), Collections.emptyList());
    }

    /** Deletes files loaded more than a day ago and never saved. */
    private void sweep() throws IOException {
        if (!Files.isDirectory(this.staging)) {
            return;
        }
        final Instant before = Instant.now().minus(STALE);
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(this.staging)) {
            for (final Path folder : folders) {
                if (ID.matcher(folder.getFileName().toString()).matches()
                    && Files.getLastModifiedTime(folder).toInstant().isBefore(before)) {
                    Files.deleteIfExists(folder.resolve(CONTENT));
                    Files.deleteIfExists(folder.resolve(NAME));
                    Files.deleteIfExists(folder);
                }
            }
        }
    }

    /** Saves a staged file with the title the operator confirmed. */
    private void save(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final String id = request.getParameter("staging");
        final Path folder = id == null || !ID.matcher(id).matches() ? null : this.staging.resolve(id);
        if (folder == null || !Files.isRegularFile(folder.resolve(CONTENT))) {
            this.problem(response, "Файл плана не найден в промежуточной зоне: загрузите его ещё раз");
            return;
        }
        final String name = new String(Files.readAllBytes(folder.resolve(NAME)), StandardCharsets.UTF_8);
        final Curriculum plan;
        try {
            plan = Curriculum.read(Files.readAllBytes(folder.resolve(CONTENT)));
        } catch (final WorkbookException error) {
            this.problem(response, error.getMessage());
            return;
        }
        final PlanHeader header = header(request);
        if (!header.missing().isEmpty()) {
            this.preview(response, id, name, plan, header, header.missing());
            return;
        }
        final Curricula.Saved saved = new Curricula(this.database).save(
            header, plan.rows(), plan.origin().startsWith("PDF") ? "pdf" : "xlsx", name.isEmpty() ? null : name,
            null, field(request, "note")
        );
        Files.deleteIfExists(folder.resolve(CONTENT));
        Files.deleteIfExists(folder.resolve(NAME));
        Files.deleteIfExists(folder);
        response.sendRedirect(
            request.getContextPath() + "/plans/" + saved.id + (saved.unchanged ? "?unchanged=1" : "")
        );
    }

    private void preview(
        final HttpServletResponse response, final String id, final String name, final Curriculum plan,
        final PlanHeader header, final List<String> missing
    ) throws IOException, SQLException {
        final PlanStructure structure = PlanStructure.of(plan.rows());
        final PlanCheck check = PlanCheck.of(structure);
        final StringBuilder body = new StringBuilder("<h1>Учебный план прочитан</h1><p>")
            .append(Html.escape(name)).append(", ").append(Html.escape(plan.origin())).append(", строк: ")
            .append(plan.rows().size()).append(". Проверьте шапку и сохраните.</p>");
        if (!missing.isEmpty()) {
            body.append("<section class=\"error\">Заполните: ").append(Html.escape(String.join(", ", missing)))
                .append("</section>");
        }
        body.append("<form method=\"post\" action=\"plans/save\"><section><h2>Шапка</h2>")
            .append("<input type=\"hidden\" name=\"staging\" value=\"").append(id).append("\">")
            .append(headerFields(header))
            .append("<label>Примечание (для журнала)</label><input type=\"text\" name=\"note\" value=\"\">")
            .append("<br><button>Сохранить</button></section></form>");
        body.append("<section><h2>Итоги</h2>").append(PlanView.totals(plan.rows())).append("</section>")
            .append("<section><h2>Контрольные суммы</h2>").append(PlanView.check(check)).append("</section>");
        if (header.missing().isEmpty()) {
            body.append(this.comparison(header, structure, null, null));
        }
        body.append("<section><h2>План</h2>").append(PlanView.table(structure, check)).append("</section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /** An edition: its title, totals, sums, changes, table and history. */
    private void edition(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Curricula curricula = new Curricula(this.database);
        final Curricula.Edition edition = curricula.find(id);
        if (edition == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final List<PlanRow> rows = curricula.rows(id);
        final PlanStructure structure = PlanStructure.of(rows);
        final PlanCheck check = PlanCheck.of(structure);
        final StringBuilder body = new StringBuilder("<h1>Учебный план, редакция ").append(edition.edition)
            .append("</h1>");
        if (request.getParameter("unchanged") != null) {
            body.append("<p class=\"note\">Этот план уже загружен: файл совпадает с редакцией ")
                .append(edition.edition).append(", новая редакция не создана.</p>");
        }
        final Curricula.Edition latest = curricula.latest(edition.header);
        if (latest != null && !latest.id.equals(id)) {
            body.append("<p class=\"note\">Есть более новая редакция: <a href=\"plans/").append(latest.id)
                .append("\">").append(latest.edition).append("</a>.</p>");
        }
        body.append("<section>").append(PlanView.header(edition.header)).append("<p class=\"muted\">Источник: ")
            .append(Html.escape(source(edition))).append(". Сохранено ").append(Html.escape(PlanView.time(edition.createdAt)))
            .append(".</p><p><a href=\"plans/").append(id).append("/edit\">Править</a></p>")
            .append("<form method=\"get\" action=\"plans/").append(id).append("/derive\">")
            .append("<b>Создать на основе</b> план ")
            .append("<select name=\"form\"><option>очная</option><option>заочная</option>")
            .append("<option>очно-заочная</option></select> на год набора ")
            .append("<input type=\"number\" name=\"year\" min=\"1990\" max=\"2100\" style=\"width:6em\" value=\"")
            .append(Html.escape(edition.header.year())).append("\"> <button>Создать</button></form></section>");
        body.append("<section><h2>Итоги</h2>").append(PlanView.totals(rows)).append("</section>")
            .append("<section><h2>Контрольные суммы</h2>").append(PlanView.check(check)).append("</section>");
        if (edition.basedOn != null) {
            final Curricula.Edition base = curricula.find(edition.basedOn);
            if (base != null) {
                body.append("<section><h2>Изменения при правке</h2>")
                    .append(PlanView.diff(
                        PlanDiff.of(PlanStructure.of(curricula.rows(base.id)), structure), link(base)
                    )).append("</section>");
            }
        }
        body.append(this.comparison(edition.header, structure, id, edition.basedOn))
            .append("<section><h2>План</h2>").append(PlanView.table(structure, check)).append("</section>")
            .append("<section><h2>Журнал</h2><ul>");
        for (final String entry : curricula.history(id)) {
            body.append("<li>").append(Html.escape(entry)).append("</li>");
        }
        body.append("</ul></section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /**
     * The form to correct an edition, or to make from it the plan of another
     * form or year; contact and self-study hours are cleared for another form.
     */
    private void form(
        final String id, final boolean derive, final HttpServletRequest request, final HttpServletResponse response
    ) throws IOException, SQLException {
        final Curricula curricula = new Curricula(this.database);
        final Curricula.Edition edition = curricula.find(id);
        if (edition == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        PlanHeader header = edition.header;
        List<PlanRow> rows = curricula.rows(id);
        final StringBuilder body = new StringBuilder();
        if (derive) {
            header = new PlanHeader(
                header.code(), header.direction(), header.profile(), header.qualification(), field(request, "form"),
                header.studyTerm(), field(request, "year")
            );
            body.append("<h1>Новый план на основе редакции ").append(edition.edition).append("</h1><p>")
                .append(link(edition)).append(" → ").append(Html.escape(header.studyForm() + ", " + header.year()))
                .append(". Исправьте отличия по скану или документу, проверьте суммы и сохраните.</p>");
            if (!header.studyForm().equals(edition.header.studyForm())) {
                rows = withoutFormHours(rows);
                body.append("<p class=\"note\">Форма обучения другая: часы контактной и самостоятельной работы ")
                    .append("очищены. Впишите их в итоговую строку «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ» по документу.</p>");
            }
        } else {
            body.append("<h1>Правка учебного плана, редакция ").append(edition.edition).append("</h1>")
                .append("<p>Сохранение создаст новую редакцию, эта останется как есть.</p>");
        }
        body.append(new PlanForm(header, rows, "", Collections.emptyList()).html("plans/" + id + "/edit"));
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /** A corrected or derived plan: checked and shown again, or saved as a new edition. */
    private void edited(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Curricula curricula = new Curricula(this.database);
        final Curricula.Edition base = curricula.find(id);
        if (base == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final PlanForm form = PlanForm.read(request);
        final PlanStructure structure = PlanStructure.of(form.rows());
        final PlanCheck check = PlanCheck.of(structure);
        final List<String> problems = new ArrayList<>(form.problems());
        for (final String missing : form.header().missing()) {
            problems.add("Заполните: " + missing);
        }
        final boolean save = "save".equals(field(request, "action"));
        if (save && problems.isEmpty() && (check.passed() || !field(request, "force").isEmpty())) {
            final Curricula.Saved saved = curricula.save(form.header(), form.rows(), "manual", null, id, form.note());
            response.sendRedirect(
                request.getContextPath() + "/plans/" + saved.id + (saved.unchanged ? "?unchanged=1" : "")
            );
            return;
        }
        final StringBuilder body = new StringBuilder("<h1>Правка учебного плана</h1>");
        if (save && problems.isEmpty()) {
            body.append("<section class=\"error\">Суммы не сходятся. Исправьте строки или отметьте ")
                .append("«Сохранить, даже если суммы не сходятся».</section>");
        }
        for (final String problem : problems) {
            body.append("<p class=\"error\">").append(Html.escape(problem)).append("</p>");
        }
        body.append("<section><h2>Контрольные суммы</h2>").append(PlanView.check(check)).append("</section>")
            .append("<section><h2>Изменения</h2>")
            .append(PlanView.diff(PlanDiff.of(PlanStructure.of(curricula.rows(id)), structure), link(base)))
            .append("</section>")
            .append(form.html("plans/" + id + "/edit"));
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /**
     * Changes from the nearest plan of the program, or why there are none to
     * show; the plan an edition was made from is compared above it.
     */
    private String comparison(
        final PlanHeader header, final PlanStructure plan, final String self, final String basedOn
    ) throws SQLException {
        final Curricula curricula = new Curricula(this.database);
        final Curricula.Edition nearest = curricula.nearest(header);
        final StringBuilder html = new StringBuilder("<section><h2>Сравнение с ближайшим планом</h2>");
        if (nearest == null || nearest.id.equals(self)) {
            html.append("<p class=\"muted\">Других планов этой программы нет: сравнивать не с чем.</p>");
        } else if (nearest.id.equals(basedOn)) {
            html.append("<p class=\"muted\">Ближайший план — тот, на основе которого сделана эта редакция: ")
                .append("отличия от него показаны выше.</p>");
        } else {
            html.append(PlanView.diff(PlanDiff.of(PlanStructure.of(curricula.rows(nearest.id)), plan), link(nearest)));
        }
        return html.append("</section>").toString();
    }

    private void problem(final HttpServletResponse response, final String message) throws IOException {
        Responses.html(
            response, HttpServletResponse.SC_BAD_REQUEST,
            Html.page(TITLE, "<h1>" + TITLE + "</h1><section class=\"error\">" + Html.escape(message)
                + "</section><p><a href=\"plans\">К учебным планам</a></p>")
        );
    }

    private static String headerFields(final PlanHeader header) {
        return field("Код направления", "code", header.code())
            + field("Наименование направления", "direction", header.direction())
            + field("Профиль", "profile", header.profile())
            + field("Квалификация", "qualification", header.qualification())
            + field("Форма обучения", "form", header.studyForm())
            + field("Срок обучения", "term", header.studyTerm())
            + field("Год набора", "year", header.year());
    }

    private static String field(final String label, final String name, final String value) {
        return "<label>" + Html.escape(label) + "</label><input type=\"text\" name=\"" + name + "\" value=\""
            + Html.escape(value) + "\">";
    }

    /** The path within the application, whatever prefix a proxy serves it under. */
    private static String path(final HttpServletRequest request) {
        return request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    }

    private static PlanHeader header(final HttpServletRequest request) {
        return new PlanHeader(
            field(request, "code"), field(request, "direction"), field(request, "profile"),
            field(request, "qualification"), field(request, "form"), field(request, "term"), field(request, "year")
        );
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }

    /** «очная, 2021, редакция 2» as a link. */
    private static String link(final Curricula.Edition edition) {
        return "«<a href=\"plans/" + edition.id + "\">" + Html.escape(
            edition.header.studyForm() + ", " + edition.header.year() + ", редакция " + edition.edition
        ) + "</a>»";
    }

    private static String source(final Curricula.Edition edition) {
        final String kind = "pdf".equals(edition.source) ? "PDF" : "xlsx".equals(edition.source) ? "Excel"
            : "введён вручную";
        return edition.sourceFile == null ? kind : kind + ", " + edition.sourceFile;
    }

    /** The rows with contact and self-study hours cleared. */
    private static List<PlanRow> withoutFormHours(final List<PlanRow> rows) {
        final List<PlanRow> cleared = new ArrayList<>();
        for (final PlanRow row : rows) {
            cleared.add(new PlanRow(
                row.index(), row.name(), row.controls(), row.creditColumns(), row.hours().subList(0, FORM_HOURS)
            ));
        }
        return cleared;
    }
}
