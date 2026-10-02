package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.export.CatalogSource;
import org.opendiplom.export.CyberDiplomaXml;
import org.opendiplom.export.Program;
import org.opendiplom.export.ProgramFields;
import org.opendiplom.export.StudentInfo;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.graduation.Results;
import org.opendiplom.graduation.StudentMatch;
import org.opendiplom.graduation.SubjectMatch;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;
import org.opendiplom.storage.Curricula;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Graduations;

/**
 * A graduation from the statement and the information file of a group
 * (ADR-0009): the files wait in the staging zone while the operator matches
 * students and subjects to the plan; confirmed, the graduates go to the
 * registry, where they are checked and exported to CyberDiploma.
 */
final class GraduationsPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Выпуски";
    private static final Pattern ROUTE = Pattern.compile(
        "^/graduations(?:/(new)|/([0-9a-f-]{36})(?:/(match|xml|delete)|/graduates/([0-9a-f-]{36}))?)?/?$"
    );
    private static final String STATEMENT = "statement.bin";
    private static final String INFO = "info.bin";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final transient Database database;
    private final transient Path staging;

    GraduationsPage(final Database database, final Path staging) {
        this.database = database;
        this.staging = staging;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Matcher route = ROUTE.matcher(path(request));
        try {
            if (!route.matches()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if ("new".equals(route.group(1))) {
                this.fresh(request, response);
            } else if (route.group(2) == null) {
                this.list(response);
            } else if (route.group(4) != null) {
                this.card(route.group(2), route.group(4), response);
            } else if ("match".equals(route.group(3))) {
                this.match(route.group(2), response, Collections.emptyList());
            } else if ("xml".equals(route.group(3))) {
                this.xml(route.group(2), response);
            } else if (route.group(3) == null) {
                this.graduation(route.group(2), request, response);
            } else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
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
            if (!route.matches() || route.group(4) != null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            } else if ("new".equals(route.group(1))) {
                this.upload(request, response);
            } else if ("match".equals(route.group(3))) {
                this.matched(route.group(2), request, response);
            } else if ("delete".equals(route.group(3))) {
                this.delete(route.group(2), request, response);
            } else if (route.group(2) != null && route.group(3) == null) {
                this.chairman(route.group(2), request, response);
            } else {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } catch (final SQLException error) {
            throw new ServletException("Не удалось сохранить выпуск", error);
        }
    }

    private void list(final HttpServletResponse response) throws IOException, SQLException {
        final Graduations graduations = new Graduations(this.database);
        final Curricula curricula = new Curricula(this.database);
        final StringBuilder body = new StringBuilder("<h1>").append(TITLE).append("</h1>")
            .append("<section><h2>Новый выпуск</h2><p>Ведомость и сведения о студентах одной группы. Программа ")
            .append("определит форму обучения и год набора, предложит учебный план и сопоставит студентов и ")
            .append("предметы; в картотеку выпуск попадёт после подтверждения.</p>")
            .append(GraduationView.upload("")).append("</section><section><h2>Выпуски</h2>");
        final List<Graduations.Graduation> all = graduations.all();
        if (all.isEmpty()) {
            body.append("<p class=\"muted\">Пока нет ни одного.</p>");
        } else {
            body.append("<table><tr><th>Группа</th><th>Где</th><th>Форма</th><th>Год набора</th>")
                .append("<th>Учебный план</th><th>Выпускников</th><th>Изменён</th></tr>");
            for (final Graduations.Graduation graduation : all) {
                final Curricula.Edition edition = graduation.curriculumId == null ? null
                    : curricula.find(graduation.curriculumId);
                body.append("<tr><td><a href=\"graduations/").append(graduation.id)
                    .append(graduation.staging() ? "/match" : "").append("\">")
                    .append(Html.escape(graduation.groupName)).append("</a></td><td>")
                    .append(graduation.staging() ? "промежуточная зона" : "картотека").append("</td><td>")
                    .append(Html.escape(graduation.studyForm)).append("</td><td>")
                    .append(graduation.admissionYear == null ? "" : graduation.admissionYear).append("</td><td>")
                    .append(edition == null ? "—" : Html.escape(edition.header.code() + " " + edition.header.profile()
                        + ", редакция " + edition.edition))
                    .append("</td><td>").append(graduation.staging() ? "" : String.valueOf(graduations.graduates(graduation.id).size()))
                    .append("</td><td>").append(Html.escape(PlanView.time(graduation.updatedAt))).append("</td></tr>");
            }
            body.append("</table>");
        }
        body.append("</section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    private void fresh(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final String group = request.getParameter("group") == null ? "" : request.getParameter("group").strip();
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE,
            "<h1>Новый выпуск</h1><section>" + GraduationView.upload(group) + "</section>"));
    }

    /** Reads both files, keeps them in the staging zone and goes on to matching. */
    private void upload(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException, SQLException {
        final Uploads uploads = new Uploads(request);
        final byte[] statementFile = uploads.file("statement");
        final byte[] infoFile = uploads.file("info");
        final String group = uploads.field("group");
        if (statementFile == null || infoFile == null || group.isEmpty()) {
            this.problem(response, "Укажите группу и выберите оба файла: ведомость и сведения о студентах");
            return;
        }
        final StatementImport statement;
        try {
            statement = StatementImport.read(statementFile);
            info(infoFile);
        } catch (final WorkbookException | ValidationProblems error) {
            this.problem(response, error.getMessage());
            return;
        }
        final Graduations graduations = new Graduations(this.database);
        final String id = graduations.stage(
            group, statement.form(), statement.admissionYear(), name(uploads.fileName("statement")),
            name(uploads.fileName("info"))
        );
        final Path folder = Files.createDirectories(this.staging.resolve(id));
        Files.write(folder.resolve(STATEMENT), statementFile);
        Files.write(folder.resolve(INFO), infoFile);
        // the only curriculum of the form and year needs no choosing
        if (statement.admissionYear() != null) {
            final List<Curricula.Edition> editions =
                new Curricula(this.database).of(statement.form(), statement.admissionYear());
            final Set<String> programs = new LinkedHashSet<>();
            editions.forEach(edition -> programs.add(edition.programId));
            if (programs.size() == 1) {
                graduations.curriculum(id, editions.get(0).id);
            }
        }
        response.sendRedirect(request.getContextPath() + "/graduations/" + id + "/match");
    }

    /** The matching report with the operator's choices. */
    private void match(final String id, final HttpServletResponse response, final List<String> messages)
        throws IOException, SQLException {
        final Staged staged = this.staged(id, response);
        if (staged == null) {
            return;
        }
        final Graduations.Graduation graduation = staged.graduation;
        final StringBuilder body = new StringBuilder("<h1>Выпуск ").append(Html.escape(graduation.groupName))
            .append(": сопоставление</h1><p>Ведомость «").append(Html.escape(graduation.statementFile))
            .append("», сведения «").append(Html.escape(graduation.infoFile)).append("». Форма обучения: ")
            .append(Html.escape(graduation.studyForm.isEmpty() ? "не указана" : graduation.studyForm))
            .append(", год набора: ").append(graduation.admissionYear == null ? "не определён" : graduation.admissionYear)
            .append(". Выпуск в промежуточной зоне: в картотеку он попадёт после подтверждения.</p>");
        for (final String message : messages) {
            body.append("<section class=\"error\">").append(Html.escape(message)).append("</section>");
        }
        list(body, "Замечания к ведомости", staged.statement.headerProblems());
        list(body, "Замечания к файлу сведений (войдут в проверку выпускников)", staged.info.problems());
        final List<Curricula.Edition> editions = new ArrayList<>();
        if (graduation.admissionYear != null) {
            editions.addAll(new Curricula(this.database).of(graduation.studyForm, graduation.admissionYear));
        }
        if (graduation.curriculumId != null && editions.stream().noneMatch(e -> e.id.equals(graduation.curriculumId))) {
            editions.add(0, new Curricula(this.database).find(graduation.curriculumId));
        }
        body.append("<form method=\"post\" action=\"graduations/").append(id).append("/match\">")
            .append(GraduationView.curricula(editions, graduation.curriculumId, graduation.studyForm, graduation.admissionYear))
            .append(GraduationView.students(staged.students, staged.statement.students(), staged.choices));
        if (staged.subjects != null) {
            body.append(GraduationView.subjects(staged.subjects));
        }
        final List<String> open = staged.open();
        body.append("<section><button name=\"action\" value=\"save\">Сохранить выбор</button> ");
        if (open.isEmpty()) {
            body.append("<button name=\"action\" value=\"confirm\">Подтвердить и записать в картотеку</button>");
        } else {
            body.append("<span class=\"note\">Подтвердить пока нельзя: ")
                .append(Html.escape(String.join("; ", open))).append(".</span>");
        }
        body.append("</section></form><form method=\"post\" action=\"graduations/").append(id)
            .append("/delete\"><button>Удалить выпуск из промежуточной зоны</button></form>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    /** Keeps the choices and, when asked and nothing is left open, writes the graduation to the registry. */
    private void matched(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Graduations graduations = new Graduations(this.database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation == null || !graduation.staging()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final String curriculum = field(request, "curriculum");
        final boolean replanned = !curriculum.equals(graduation.curriculumId == null ? "" : graduation.curriculumId);
        if (replanned) {
            final Curricula.Edition edition = curriculum.isEmpty() ? null : new Curricula(this.database).find(curriculum);
            graduations.curriculum(id, edition == null ? null : edition.id);
        }
        final Map<String, String> choices = new LinkedHashMap<>();
        for (final String name : Collections.list(request.getParameterNames())) {
            if (name.startsWith(StudentMatch.INFO) || name.startsWith(StudentMatch.SHEET)) {
                choices.put(name, field(request, name));
            }
        }
        // the elements of another edition are other positions: its subjects are matched anew
        graduations.choose(id, choices, replanned ? SubjectMatch.PREFIX : null);
        if (!replanned) {
            this.subjectChoices(id, request);
        }
        if (!"confirm".equals(field(request, "action"))) {
            response.sendRedirect(request.getContextPath() + "/graduations/" + id + "/match");
            return;
        }
        final Staged staged = this.staged(id, response);
        if (staged == null) {
            return;
        }
        if (!staged.open().isEmpty()) {
            this.match(id, response, Collections.singletonList(
                "Подтвердить нельзя: " + String.join("; ", staged.open())
            ));
            return;
        }
        final List<GraduateRecord> graduates = new ArrayList<>();
        for (final StudentMatch.Pair pair : staged.students.graduates()) {
            final StudentInfo.Entry entry = pair.entry;
            graduates.add(new GraduateRecord(
                null, graduates.size(), entry.lastName, entry.firstName, entry.middleName, entry.birthDate,
                entry.previousDocument, entry.previousYear, entry.gekDate, entry.gekProtocol, entry.thesisTopic,
                entry.thesisGrade, entry.stateExamGrade, pair.student, staged.statement.sheet(pair.student).number(),
                String.join("\n", entry.problems), Results.of(staged.statement, pair.student, staged.subjects)
            ));
        }
        final List<SubjectMatch.Link> links = new ArrayList<>();
        for (final SubjectMatch.Row row : staged.subjects.rows()) {
            if (row.status == SubjectMatch.Status.CHOSEN || row.status == SubjectMatch.Status.EXCLUDED) {
                links.add(SubjectMatch.Link.of(row.subject.key(), staged.subjects.plan(), row.target));
            }
        }
        final String registered = graduations.register(id, graduates, staged.edition.programId, links);
        this.forget(id);
        response.sendRedirect(request.getContextPath() + "/graduations/" + registered);
    }

    /**
     * Keeps a subject's choice only when it is the operator's: another element
     * than found, or the confirmation of a proposal or of the element chosen.
     */
    private void subjectChoices(final String id, final HttpServletRequest request) throws SQLException, IOException {
        final Graduations graduations = new Graduations(this.database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation.curriculumId == null) {
            return;
        }
        final Map<String, String> without = new HashMap<>(graduations.choices(id));
        without.keySet().removeIf(item -> item.startsWith(SubjectMatch.PREFIX));
        final Staged found = this.read(graduation, without);
        if (found == null || found.subjects == null) {
            return;
        }
        final Map<String, String> choices = new LinkedHashMap<>();
        for (final SubjectMatch.Row row : found.subjects.rows()) {
            final String item = SubjectMatch.PREFIX + row.subject.key();
            final String value = request.getParameter(item);
            if (value == null) {
                continue;
            }
            final boolean same = row.target != null && row.target.code().equals(value);
            choices.put(item, row.status.automatic() && same ? "" : value);
        }
        graduations.choose(id, choices, null);
    }

    private void delete(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Graduations.Graduation graduation = new Graduations(this.database).find(id);
        if (graduation == null || !graduation.staging()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        new Graduations(this.database).delete(id);
        this.forget(id);
        response.sendRedirect(request.getContextPath() + "/graduations");
    }

    /** A registered graduation: its program, graduates with their checks and the XML. */
    private void graduation(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Registered registered = this.registered(id, response);
        if (registered == null) {
            return;
        }
        if (registered.graduation.staging()) {
            response.sendRedirect(request.getContextPath() + "/graduations/" + id + "/match");
            return;
        }
        final Graduations.Graduation graduation = registered.graduation;
        final PlanHeader header = registered.edition.header;
        final StringBuilder body = new StringBuilder("<h1>Выпуск ").append(Html.escape(graduation.groupName))
            .append("</h1><section>").append(PlanView.header(header)).append("<p class=\"muted\">Учебный план: ")
            .append("<a href=\"plans/").append(registered.edition.id).append("\">редакция ")
            .append(registered.edition.edition).append("</a>. Ведомость «").append(Html.escape(graduation.statementFile))
            .append("», сведения «").append(Html.escape(graduation.infoFile)).append("». Изменён ")
            .append(Html.escape(PlanView.time(graduation.updatedAt))).append(".</p><p><a href=\"graduations/new?group=")
            .append(URLEncoder.encode(graduation.groupName, StandardCharsets.UTF_8))
            .append("\">Загрузить исправленные файлы группы</a> — выпускники будут заменены.</p></section>");
        body.append("<section><h2>Программа в XML</h2><table>");
        for (final String[] field : XmlPage.FIELDS) {
            body.append("<tr><th>").append(Html.escape(field[1])).append("</th><td>")
                .append(Html.escape(registered.fields.getOrDefault(field[0], ""))).append("</td><td class=\"muted\">")
                .append(Html.escape(registered.sources.getOrDefault(field[0], ""))).append("</td></tr>");
        }
        body.append("</table><form method=\"post\" action=\"graduations/").append(id).append("\">")
            .append("<label>Председатель ГЭК (из приказа о составе ГЭК)</label><input type=\"text\" name=\"chairman\" value=\"")
            .append(Html.escape(graduation.gekChairman)).append("\"> <button>Сохранить</button></form>");
        list(body, "Что мешает XML", registered.programProblems);
        body.append("</section><section><h2>Выпускники: ").append(registered.graduates.size()).append("</h2>")
            .append(GraduationView.graduates(id, registered.graduates, registered.findings)).append("</section>");
        body.append("<section><h2>XML для КиберДиплома</h2>");
        final long blocked = registered.findings.stream().filter(own -> !Checks.exportable(own)).count();
        final long unfinished = registered.findings.stream()
            .filter(own -> Checks.exportable(own) && !Checks.ready(own)).count();
        if (blocked > 0 || !registered.programProblems.isEmpty()) {
            body.append("<p class=\"note\">Сформировать нельзя: ")
                .append(blocked > 0 ? "есть ошибки у выпускников: " + blocked : "исправьте программу выше")
                .append(".</p>");
        } else {
            if (unfinished > 0) {
                body.append("<p class=\"note\">Выпускников с кодом 7 «не выполнял»: ").append(unfinished)
                    .append(". Код попадёт в XML как заглушка до настоящей оценки.</p>");
            }
            body.append("<p><a href=\"graduations/").append(id).append("/xml\">Сформировать XML</a></p>");
        }
        body.append("</section><section><h2>Журнал</h2><ul>");
        for (final String entry : new Graduations(this.database).history(id)) {
            body.append("<li>").append(Html.escape(entry)).append("</li>");
        }
        body.append("</ul></section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body.toString()));
    }

    private void chairman(final String id, final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, SQLException {
        final Graduations graduations = new Graduations(this.database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation == null || graduation.staging()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        graduations.chairman(id, field(request, "chairman"));
        response.sendRedirect(request.getContextPath() + "/graduations/" + id);
    }

    private void card(final String id, final String graduate, final HttpServletResponse response)
        throws IOException, SQLException {
        final Registered registered = this.registered(id, response);
        if (registered == null) {
            return;
        }
        for (int number = 0; number < registered.graduates.size(); ++number) {
            final GraduateRecord record = registered.graduates.get(number);
            if (record.id.equals(graduate)) {
                final String body = "<h1>" + Html.escape(record.fullName()) + "</h1><p><a href=\"graduations/" + id
                    + "\">Выпуск " + Html.escape(registered.graduation.groupName) + "</a></p><section><h2>Проверка</h2>"
                    + GraduationView.findings(registered.findings.get(number)) + "</section>"
                    + GraduationView.card(record, registered.plan);
                Responses.html(response, HttpServletResponse.SC_OK, Html.page(TITLE, body));
                return;
            }
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private void xml(final String id, final HttpServletResponse response) throws IOException, SQLException {
        final Registered registered = this.registered(id, response);
        if (registered == null) {
            return;
        }
        final List<String> problems = new ArrayList<>(registered.programProblems);
        for (int number = 0; number < registered.graduates.size(); ++number) {
            for (final Checks.Finding finding : registered.findings.get(number)) {
                if (finding.level == Checks.Level.ERROR) {
                    problems.add(registered.graduates.get(number).fullName() + ": " + finding.message);
                }
            }
        }
        if (!problems.isEmpty() || registered.program == null) {
            this.problem(response, "XML не сформирован:\n" + String.join("\n", problems));
            return;
        }
        final Program program = registered.program;
        final String name = program.directionCode() + '_' + registered.fields.get("qualification") + '_'
            + registered.fields.get("study_form") + '_' + registered.graduation.groupName + '_'
            + LocalDateTime.now().format(STAMP) + ".xml";
        Responses.file(response, "application/xml", name, CyberDiplomaXml.write(
            program, CatalogSource.graduates(registered.graduates, Checks.stateExam(registered.plan))
        ).getBytes(StandardCharsets.UTF_8));
    }

    /** A graduation in the staging zone with its files read and matched; {@code null} after an error page. */
    private Staged staged(final String id, final HttpServletResponse response) throws IOException, SQLException {
        final Graduations graduations = new Graduations(this.database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        if (!graduation.staging()) {
            response.sendRedirect("../" + id);
            return null;
        }
        final Staged staged = this.read(graduation, graduations.choices(id));
        if (staged == null) {
            this.problem(response, "Файлы выпуска не найдены в промежуточной зоне или больше не читаются: "
                + "удалите выпуск и загрузите файлы ещё раз");
        }
        return staged;
    }

    /** The files of a staged graduation matched with the choices, {@code null} when they cannot be read. */
    private Staged read(final Graduations.Graduation graduation, final Map<String, String> choices)
        throws IOException, SQLException {
        final Path folder = this.staging.resolve(graduation.id);
        if (!Files.isRegularFile(folder.resolve(STATEMENT)) || !Files.isRegularFile(folder.resolve(INFO))) {
            return null;
        }
        final StatementImport statement;
        final StudentInfo info;
        try {
            statement = StatementImport.read(Files.readAllBytes(folder.resolve(STATEMENT)));
            info = info(Files.readAllBytes(folder.resolve(INFO)));
        } catch (final WorkbookException | ValidationProblems error) {
            return null;
        }
        final StudentMatch students = StudentMatch.of(info.entries(), statement.students(), choices);
        Curricula.Edition edition = null;
        SubjectMatch subjects = null;
        if (graduation.curriculumId != null) {
            final Curricula curricula = new Curricula(this.database);
            edition = curricula.find(graduation.curriculumId);
            final List<String> taking = new ArrayList<>(statement.students());
            for (final StudentMatch.Pair pair : students.with(StudentMatch.Status.EXCLUDED)) {
                taking.remove(pair.student);
            }
            subjects = SubjectMatch.of(
                statement, taking, PlanStructure.of(curricula.rows(edition.id)),
                new Graduations(this.database).links(edition.programId), choices
            );
        }
        return new Staged(graduation, statement, info, choices, students, edition, subjects);
    }

    /** A registered graduation with its plan, graduates and checks; {@code null} after an error page. */
    private Registered registered(final String id, final HttpServletResponse response) throws IOException, SQLException {
        final Graduations graduations = new Graduations(this.database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        final Curricula curricula = new Curricula(this.database);
        final Curricula.Edition edition = graduation.curriculumId == null ? null : curricula.find(graduation.curriculumId);
        if (edition == null) {
            return new Registered(graduation, null, null, null, Collections.emptyList());
        }
        final List<PlanRow> rows = curricula.rows(edition.id);
        return new Registered(graduation, edition, PlanStructure.of(rows), PlanTotals.of(rows), graduations.graduates(id));
    }

    private static StudentInfo info(final byte[] content) throws WorkbookException, ValidationProblems {
        final List<Sheet> sheets = Workbooks.read(content, "Сведения о студентах");
        if (sheets.isEmpty()) {
            throw new WorkbookException("Сведения о студентах: в книге нет листов");
        }
        return StudentInfo.read(sheets.get(0), LocalDate.now());
    }

    private void forget(final String id) throws IOException {
        final Path folder = this.staging.resolve(id);
        Files.deleteIfExists(folder.resolve(STATEMENT));
        Files.deleteIfExists(folder.resolve(INFO));
        Files.deleteIfExists(folder);
    }

    private void problem(final HttpServletResponse response, final String message) throws IOException {
        Responses.html(
            response, HttpServletResponse.SC_BAD_REQUEST,
            Html.page(TITLE, "<h1>" + TITLE + "</h1><section class=\"error\">" + Html.escape(message)
                + "</section><p><a href=\"graduations\">К выпускам</a></p>")
        );
    }

    private static void list(final StringBuilder body, final String title, final List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        body.append("<section class=\"note\"><b>").append(Html.escape(title)).append("</b><ul>");
        for (final String item : items) {
            body.append("<li>").append(Html.escape(item)).append("</li>");
        }
        body.append("</ul></section>");
    }

    private static String name(final String file) {
        return file == null ? "" : file;
    }

    /** The path within the application, whatever prefix a proxy serves it under. */
    private static String path(final HttpServletRequest request) {
        return request.getServletPath() + (request.getPathInfo() == null ? "" : request.getPathInfo());
    }

    private static String field(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        return value == null ? "" : value.strip();
    }

    /** What the staging page shows. */
    private static final class Staged {
        final Graduations.Graduation graduation;
        final StatementImport statement;
        final StudentInfo info;
        final Map<String, String> choices;
        final StudentMatch students;
        final Curricula.Edition edition;
        final SubjectMatch subjects;

        Staged(
            final Graduations.Graduation graduation, final StatementImport statement, final StudentInfo info,
            final Map<String, String> choices, final StudentMatch students, final Curricula.Edition edition,
            final SubjectMatch subjects
        ) {
            this.graduation = graduation;
            this.statement = statement;
            this.info = info;
            this.choices = choices;
            this.students = students;
            this.edition = edition;
            this.subjects = subjects;
        }

        /** What keeps the graduation from the registry; empty when nothing. */
        List<String> open() {
            final List<String> open = new ArrayList<>();
            if (this.subjects == null) {
                open.add("не выбран учебный план");
            }
            final long students = this.students.pairs().stream().filter(pair -> pair.status.open()).count();
            if (students > 0) {
                open.add("не связаны студенты: " + students);
            }
            if (this.students.graduates().isEmpty()) {
                open.add("нет ни одного выпускника");
            }
            if (this.subjects != null) {
                final long subjects = this.subjects.rows().stream().filter(row -> row.status.open()).count();
                if (subjects > 0) {
                    open.add("не связаны предметы: " + subjects);
                }
            }
            return open;
        }
    }

    /** What the pages of a registered graduation show. */
    private static final class Registered {
        final Graduations.Graduation graduation;
        final Curricula.Edition edition;
        final PlanStructure plan;
        final List<GraduateRecord> graduates;
        final List<List<Checks.Finding>> findings = new ArrayList<>();
        final Map<String, String> sources = new HashMap<>();
        final Map<String, String> fields;
        final List<String> programProblems = new ArrayList<>();
        final Program program;

        Registered(
            final Graduations.Graduation graduation, final Curricula.Edition edition, final PlanStructure plan,
            final PlanTotals totals, final List<GraduateRecord> graduates
        ) {
            this.graduation = graduation;
            this.edition = edition;
            this.plan = plan;
            this.graduates = graduates;
            if (edition == null) {
                this.fields = Collections.emptyMap();
                this.programProblems.add("у выпуска нет учебного плана");
                this.program = null;
                return;
            }
            final PlanHeader header = edition.header;
            this.fields = ProgramFields.of(
                header.code(), header.direction(), header.profile(), header.qualification(), header.studyForm(),
                header.studyTerm(), totals, this.sources
            );
            if (!graduation.gekChairman.isEmpty()) {
                this.fields.put("gek_chairman", graduation.gekChairman);
                this.sources.put("gek_chairman", "введён на этой странице");
            }
            for (final String[] field : XmlPage.FIELDS) {
                if (!this.fields.containsKey(field[0])) {
                    this.programProblems.add("«" + field[1] + "»: " + this.sources.get(field[0]));
                }
            }
            Program built = null;
            try {
                built = ProgramFields.program(this.fields);
            } catch (final ValidationProblems error) {
                this.programProblems.addAll(error.problems());
            }
            this.program = this.programProblems.isEmpty() ? built : null;
            for (final GraduateRecord record : graduates) {
                this.findings.add(Checks.of(record, plan, totals));
            }
        }
    }
}
