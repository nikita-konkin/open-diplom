package org.opendiplom.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import org.opendiplom.Settings;
import org.opendiplom.printing.Fonts;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.ImportBatches;

/** Main page: what the prototype can do, and the latest imports. */
final class HomePage extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private final transient Settings settings;
    private final transient Database database;

    HomePage(final Settings settings, final Database database) {
        this.settings = settings;
        this.database = database;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException {
        if (!"/".equals(request.getRequestURI())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        final Optional<Path> font = Fonts.serif(this.settings.font());
        final StringBuilder body = new StringBuilder("<h1>Открытый диплом</h1>");
        body.append("<p class=\"muted\">Режим: ")
            .append(this.settings.desktop() ? "один компьютер" : "сервер")
            .append(" · Java ").append(Html.escape(System.getProperty("java.version")))
            .append(" · шрифт для печати: ")
            .append(font.map(p -> Html.escape(p.getFileName())).orElse("не найден"))
            .append("</p>");
        body.append("<section><h2>Импорт ведомости</h2>")
            .append("<p>Ведомость «Деканата» (.xls или .xlsx, лист на студента). З.е. берутся из ")
            .append("учебного плана, а без него считаются по часам и помечаются для проверки.</p>")
            .append("<form method=\"post\" action=\"/import\" enctype=\"multipart/form-data\">")
            .append("<label>Ведомость</label><input type=\"file\" name=\"statement\" accept=\".xls,.xlsx\" required>")
            .append("<label>Учебный план (необязательно): Excel или PDF, сохранённый из «Планов»</label>")
            .append("<input type=\"file\" name=\"curriculum\" accept=\".xls,.xlsx,.pdf\">")
            .append("<br><button>Прочитать</button></form></section>");
        body.append("<section><h2>Калибровка принтера</h2>")
            .append("<form method=\"get\" action=\"/test-sheet.pdf\">")
            .append("<label>Поправка вправо, мм</label><input type=\"number\" step=\"0.1\" name=\"dx\" value=\"0\">")
            .append("<label>Поправка вниз, мм</label><input type=\"number\" step=\"0.1\" name=\"dy\" value=\"0\">")
            .append("<br><button>Тестовый лист (PDF)</button></form></section>");
        body.append(XmlPage.form());
        body.append("<section><h2>Последние импорты</h2>");
        try {
            body.append("<table><tr><th>Когда</th><th>Ведомость</th><th>Учебный план</th>")
                .append("<th>Студентов</th><th>Предметов</th></tr>");
            for (final ImportBatches.Batch batch : new ImportBatches(this.database).latest(10)) {
                body.append("<tr><td>").append(Html.escape(batch.createdAt))
                    .append("</td><td>").append(Html.escape(batch.statementFile))
                    .append("</td><td>").append(Html.escape(batch.curriculumFile))
                    .append("</td><td>").append(batch.students)
                    .append("</td><td>").append(batch.subjects).append("</td></tr>");
            }
            body.append("</table>");
        } catch (final SQLException error) {
            body.append("<p class=\"error\">База данных недоступна: ")
                .append(Html.escape(error.getMessage())).append("</p>");
        }
        body.append("</section>");
        Responses.html(response, HttpServletResponse.SC_OK, Html.page("Открытый диплом", body.toString()));
    }
}
