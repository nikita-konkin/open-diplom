package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opendiplom.export.CyberDiplomaXml;
import org.opendiplom.export.Graduate;
import org.opendiplom.export.PivotSource;
import org.opendiplom.export.Program;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.imports.Curriculum;
import org.opendiplom.imports.PlanTitle;
import org.opendiplom.imports.PlanTotals;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;

/** XML for CyberDiploma from a pivot and a student information file (transition period). */
final class XmlPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    static final String TITLE = "XML для КиберДиплома";
    /**
     * Normative full-time term by level (the middle of the code: 03 — бакалавриат,
     * 04 — магистратура). Supplements print it for part-time forms too, though
     * their plans say «4 года 6 месяцев» (decided by the owner 27.09.2026, B-35).
     */
    private static final Map<String, String> FULL_TIME_TERMS = Map.of("03", "4 года", "04", "2 года");
    private static final String[][] FIELDS = {
        {"direction", "Направление подготовки: код и наименование из шапки учебного плана"},
        {"profile", "Профиль (направленность)"},
        {"qualification", "Квалификация"},
        {"study_form", "Форма обучения"},
        {"study_term", "Срок обучения"},
        {"program_credits", "Объём программы, з.е."},
        {"contact_hours", "Аудиторные часы, как печатаются («3180 ак.час»)"},
        {"practice_credits", "З.е. всех практик"},
        {"final_credits", "З.е. ГИА"},
        {"gek_chairman", "Председатель ГЭК"},
    };

    static String form() {
        return form(Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * The form with values filled in.
     *
     * @param values field values by field name
     * @param sources where a value came from, or why it is missing, by field name
     */
    static String form(final Map<String, String> values, final Map<String, String> sources) {
        final StringBuilder form = new StringBuilder("<section><h2>").append(TITLE).append("</h2>")
            .append("<p>Сводная таблица текущего сервиса и сведения о студентах → файл обмена 3.5.1.</p>")
            .append("<form method=\"post\" action=\"/xml/plan\" enctype=\"multipart/form-data\">")
            .append("<label>Учебный план (Excel или PDF из «Планов») заполнит поля ниже, кроме председателя ГЭК</label>")
            .append("<input type=\"file\" name=\"curriculum\" accept=\".xls,.xlsx,.pdf\" required>")
            .append(" <button>Заполнить из плана</button></form>")
            .append("<form method=\"post\" action=\"/xml\" enctype=\"multipart/form-data\">")
            .append("<label>Сводная таблица</label><input type=\"file\" name=\"pivot\" accept=\".xls,.xlsx\" required>")
            .append("<label>Сведения о студентах</label><input type=\"file\" name=\"info\" accept=\".xls,.xlsx\" required>");
        for (final String[] field : FIELDS) {
            form.append("<label>").append(Html.escape(field[1])).append("</label><input type=\"text\" name=\"")
                .append(field[0]).append("\" value=\"").append(Html.escape(values.getOrDefault(field[0], "")))
                .append("\" required>");
            if (sources.containsKey(field[0])) {
                form.append("<div class=\"").append(values.containsKey(field[0]) ? "muted" : "note").append("\">")
                    .append(Html.escape(sources.get(field[0]))).append("</div>");
            }
        }
        return form.append("<br><button>Сформировать XML</button></form></section>").toString();
    }

    /**
     * Field values from a curriculum, in the form the 2026 files and printed
     * supplements have them: the direction name as a sentence, the
     * qualification and the form in lower case.
     *
     * @param sources filled with where each value came from, or why it is missing
     */
    static Map<String, String> fromPlan(final Curriculum plan, final Map<String, String> sources) {
        final PlanTitle title = plan.title();
        final PlanTotals totals = plan.totals();
        final Map<String, String> values = new LinkedHashMap<>();
        final String header = "из шапки плана";
        put(values, sources, "direction",
            title.code() == null || title.direction() == null ? null : title.code() + " " + sentence(title.direction()),
            header);
        put(values, sources, "profile", title.profile(), header);
        put(values, sources, "qualification", lower(title.qualification()), header);
        put(values, sources, "study_form", lower(title.studyForm()), header);
        final String normative = title.code() == null ? null : FULL_TIME_TERMS.get(title.code().substring(3, 5));
        if (normative != null && title.studyForm() != null && !"очная".equals(lower(title.studyForm()))) {
            put(values, sources, "study_term", normative,
                "нормативный срок очной формы; в плане «" + title.studyTerm() + "»");
        } else {
            put(values, sources, "study_term", title.studyTerm(), header);
        }
        put(values, sources, "program_credits", number(totals.program()), "итог плана «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»");
        put(values, sources, "contact_hours",
            totals.contact() == null ? null : number(totals.contact()) + " ак.час",
            "итог плана, колонка «Контактная работа»");
        put(values, sources, "practice_credits", number(totals.practices()), "итог «Блок 2. Практика»");
        put(values, sources, "final_credits", number(totals.attestation()),
            "итог «Блок 3. Государственная итоговая аттестация»");
        sources.put("gek_chairman", "в учебном плане нет: впишите из приказа о составе ГЭК");
        return values;
    }

    private static void put(
        final Map<String, String> values, final Map<String, String> sources, final String field,
        final String value, final String source
    ) {
        if (value == null || value.isEmpty()) {
            sources.put(field, "в учебном плане не найдено: впишите");
        } else {
            values.put(field, value);
            sources.put(field, source);
        }
    }

    /** «ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ» → «Информационные системы и технологии». */
    static String sentence(final String text) {
        if (!text.equals(text.toUpperCase(Locale.ROOT))) {
            return text;
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static String lower(final String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String number(final Double value) {
        return value == null ? null : PlanTotals.number(value);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, ServletException {
        final Uploads uploads = new Uploads(request);
        try {
            final Program program = new Program.Builder()
                .direction(uploads.field("direction"))
                .profile(uploads.field("profile"))
                .qualification(uploads.field("qualification"))
                .studyForm(uploads.field("study_form"))
                .studyTerm(uploads.field("study_term"))
                .programCredits(uploads.field("program_credits"))
                .contactHours(uploads.field("contact_hours"))
                .practiceCredits(uploads.field("practice_credits"))
                .finalCredits(uploads.field("final_credits"))
                .gekChairman(uploads.field("gek_chairman"))
                .build();
            final Sheet pivot = first(uploads.file("pivot"), "Сводная таблица");
            final Sheet info = first(uploads.file("info"), "Сведения о студентах");
            final List<Graduate> graduates = PivotSource.read(pivot, info, LocalDate.now());
            final String name = program.directionCode() + '_' + uploads.field("qualification") + '_'
                + uploads.field("study_form") + '_'
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xml";
            Responses.file(
                response, "application/xml", name,
                CyberDiplomaXml.write(program, graduates).getBytes(StandardCharsets.UTF_8)
            );
        } catch (final ValidationProblems | WorkbookException error) {
            Responses.problem(response, TITLE, error.getMessage());
        }
    }

    private static Sheet first(final byte[] content, final String title) throws WorkbookException {
        if (content == null) {
            throw new WorkbookException(title + ": файл не выбран");
        }
        final List<Sheet> sheets = Workbooks.read(content, title);
        if (sheets.isEmpty()) {
            throw new WorkbookException(title + ": в книге нет листов");
        }
        return sheets.get(0);
    }
}
