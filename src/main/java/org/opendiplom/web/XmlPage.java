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
import java.util.List;
import org.opendiplom.export.CyberDiplomaXml;
import org.opendiplom.export.Graduate;
import org.opendiplom.export.PivotSource;
import org.opendiplom.export.Program;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;

/** XML for CyberDiploma from a pivot and a student information file (transition period). */
final class XmlPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "XML для КиберДиплома";
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
        final StringBuilder form = new StringBuilder("<section><h2>").append(TITLE).append("</h2>")
            .append("<p>Сводная таблица текущего сервиса и сведения о студентах → файл обмена 3.5.1.</p>")
            .append("<form method=\"post\" action=\"/xml\" enctype=\"multipart/form-data\">")
            .append("<label>Сводная таблица</label><input type=\"file\" name=\"pivot\" accept=\".xls,.xlsx\" required>")
            .append("<label>Сведения о студентах</label><input type=\"file\" name=\"info\" accept=\".xls,.xlsx\" required>");
        for (final String[] field : FIELDS) {
            form.append("<label>").append(Html.escape(field[1])).append("</label><input type=\"text\" name=\"")
                .append(field[0]).append("\" required>");
        }
        return form.append("<br><button>Сформировать XML</button></form></section>").toString();
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
