package org.opendiplom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Books;
import org.opendiplom.Plans;
import org.opendiplom.Settings;
import org.opendiplom.Templates;
import org.opendiplom.export.StudentInfo;
import org.opendiplom.graduation.StudentMatch;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.printing.Fonts;
import org.opendiplom.storage.Curricula;
import org.opendiplom.storage.Database;

/** A graduation from the files of a group to the XML, through real HTTP. */
final class GraduationsPageTest {
    private static final String BOUNDARY = "----opendiplom";
    private static final Pattern SELECT = Pattern.compile("<select name=\"([^\"]+)\">(.*?)</select>", Pattern.DOTALL);
    private static final Pattern OPTION = Pattern.compile("<option value=\"([^\"]*)\"( selected)?>");
    private static final Pattern ID = Pattern.compile("/graduations/([0-9a-f-]{36})");

    @TempDir
    Path folder;

    private WebServer server;
    private String address;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        System.setProperty("opendiplom.data", this.folder.toString());
        System.setProperty("opendiplom.port", "0");
        final Settings settings = Settings.load(this.folder);
        final Database database = Database.open(settings.databaseUrl());
        new Curricula(database).save(
            new PlanHeader("11.03.02", "ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ", "Интеллектуальные сети",
                "Бакалавр", "очная", "4 года", "2021"),
            Plans.bachelor(), "xlsx", "План.xlsx", null, ""
        );
        this.server = new WebServer(settings, database);
        this.address = this.server.start();
    }

    @AfterEach
    void stop() throws Exception {
        this.server.stop();
        System.clearProperty("opendiplom.data");
        System.clearProperty("opendiplom.port");
    }

    /** A student who studied all the plan; further rows are added to the statement. */
    private static Object[] studied(final String name, final Object[]... more) {
        final List<Object[]> rows = new ArrayList<>(Arrays.asList(
            row("Математика", 252, null, 5, null),
            row("Математика", 36, null, null, 4),
            row("Физика", 144, 5, null, null),
            row("Преддипломная практика", 216, 5, null, null),
            row("Теория игр", 72, "V", null, null)
        ));
        rows.addAll(Arrays.asList(more));
        return student(name, rows.toArray(new Object[0][]));
    }

    private static byte[] info(final boolean stateExam) {
        return stateExam
            ? Books.info(Collections.singletonList(StudentInfo.STATE_EXAM),
                Books.graduate("Андреев Андрей Андреевич", 5.0), Books.graduate("Борисов Борис Борисович", 4.0))
            : Books.info(Collections.emptyList(),
                Books.graduate("Андреев Андрей Андреевич"), Books.graduate("Борисов Борис Борисович"));
    }

    @Test
    void cannotLoseGraduateOnWayToXml() throws Exception {
        final String match = this.upload(
            Books.statement(studied("Андреев А. А."), studied("Борисов Б. Б.")), info(true)
        );
        final String page = this.get(match).body();
        assertTrue(
            page.contains("совпало: 2") && page.contains("Автоматически: 5 из 5") && page.contains("value=\"confirm\""),
            "Two students of a plan studied in full were not matched without the operator"
        );
        final String graduation = this.confirm(match);
        assertTrue(
            this.get(graduation).body().contains("Сформировать нельзя: исправьте программу выше"),
            "The XML was offered without the chairman of the ГЭК"
        );
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        final HttpResponse<String> xml = this.get(graduation + "/xml");
        final String body = xml.body();
        assertEquals(200, xml.statusCode(), "The XML of a graduation without problems was refused: " + body);
        assertTrue(
            body.contains("<Госэкзамен><Наименование>Государственный экзамен</Наименование><Оценка>5</Оценка>")
                && body.contains("<КурсоваяРабота><Наименование>Математика (курсовая работа)</Наименование>")
                && body.contains("<Практика><Наименование>Производственная практика (преддипломная практика)</Наименование>"
                    + "<Оценка>5</Оценка><ЗачЕд>6</ЗачЕд></Практика>")
                && body.contains("<Факультатив><Наименование>Теория игр</Наименование><Оценка>6</Оценка>")
                && body.contains("<КодСпец>11.03.02</КодСпец>")
                && body.contains("<НаименованиеСпец>Инфокоммуникационные технологии и системы связи</НаименованиеСпец>")
                && body.split("<Студент>").length == 3,
            "The XML does not carry the graduates as the registry has them: " + body
        );
    }

    @Test
    void cannotAskOperatorSameLinkTwice() throws Exception {
        final byte[] statement = Books.statement(
            studied("Андреев А. А.", row("Астрономия", 72, "V", null, null)),
            studied("Борисов Б. Б.", row("Астрономия", 72, "V", null, null))
        );
        final String match = this.upload(statement, info(true));
        final Map<String, String> fields = selects(this.get(match).body());
        assertFalse(
            this.get(match).body().contains("value=\"confirm\""),
            "A subject missing from the plan could be confirmed without a choice"
        );
        fields.put("subject:дисциплина:астрономия", "-");
        fields.put("action", "save");
        this.post(match, fields);
        final String first = this.confirm(match);
        final String again = this.upload(statement, info(true));
        assertTrue(
            this.get(again).body().contains("Автоматически: 6 из 6"),
            "The subject the operator left out was asked about again"
        );
        assertEquals(
            first, this.confirm(again),
            "Loading the same group again made a second graduation instead of replacing the first"
        );
    }

    @Test
    void cannotAskAgainWhenFullerStatementComes() throws Exception {
        // the practice is not graded yet when the first statement comes
        final Object[] early = student("Андреев А. А.",
            row("Математика", 252, null, 5, null), row("Математика", 36, null, null, 4), row("Физика", 144, 5, null, null),
            row("Преддипломная практика", 216, null, null, null), row("Теория игр", 72, "V", null, null));
        final String match = this.upload(
            Books.statement(early, studied("Андреев А. Б."), studied("Борисов Б. Б.")),
            Books.info(Collections.singletonList(StudentInfo.STATE_EXAM),
                Books.graduate("Андреев Андрей", 5.0), Books.graduate("Борисов Борис Борисович", 4.0))
        );
        final Map<String, String> fields = selects(this.get(match).body());
        for (final String item : fields.keySet()) {
            if (item.startsWith(StudentMatch.INFO + "андреев")) {
                fields.put(item, "Андреев А. А.");
            }
        }
        fields.put(StudentMatch.SHEET + "Андреев А. Б.", StudentMatch.EXCLUDED);
        fields.put("action", "save");
        this.post(match, fields);
        final String graduation = this.confirm(match);
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        assertTrue(this.get(graduation).body().contains("<td>ошибки</td>"), "A practice without a grade was not reported");
        final String again = this.upload(
            Books.statement(studied("Борисов Б. Б."), studied("Андреев А. Б."), studied("Андреев А. А.")),
            Books.info(Collections.singletonList(StudentInfo.STATE_EXAM),
                Books.graduate("Борисов Борис Борисович", 4.0), Books.graduate("Андреев Андрей", 5.0))
        );
        final String page = this.get(again).body();
        assertTrue(
            page.contains("связано вручную: 1") && page.contains("не включён в выпуск: 1") && page.contains("value=\"confirm\""),
            "The operator was asked again about the students of a group loaded with a fuller statement"
        );
        assertEquals(graduation, this.confirm(again), "The fuller statement did not replace the graduates in place");
        assertEquals(200, this.get(graduation + "/xml").statusCode(), "The grade that came later did not reach the XML");
    }

    @Test
    void cannotPrintBeforeDocumentIsComplete() throws Exception {
        final String graduation = this.confirm(this.upload(
            Books.statement(studied("Андреев А. А."), studied("Борисов Б. Б.")), info(true)
        ));
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        assertTrue(
            this.get(graduation).body().contains("Готовы к печати: 0 из 2"),
            "Documents without numbers, date of issue or the organization were ready to print"
        );
        this.post("organization", Map.of(
            "full_name", "федеральное государственное\r\nбюджетное образовательное учреждение", "locality",
            "г. Йошкар-Ола", "head_last_name", "Петров", "head_first_name", "Пётр", "head_middle_name", "Петрович"
        ));
        final HttpResponse<String> given = this.post(graduation + "/numbers",
            Map.of("numbers", "10001, 10010-10011", "issue_date", "2026-07-03"));
        final String page = this.get(given.headers().firstValue("Location").orElse("").replaceFirst("^.*?/graduations", "graduations")).body();
        assertTrue(
            page.contains("Готовы к печати: 2 из 2") && page.contains("<td>10001</td>") && page.contains("<td>10010</td>")
                && page.contains("лишние номера не использованы: 10011"),
            "The numbers with a gap did not go to the graduates in order: " + page
        );
        final Matcher card = Pattern.compile("graduations/[0-9a-f-]{36}/graduates/[0-9a-f-]{36}").matcher(page);
        assertTrue(card.find(), "No card of a graduate on the page");
        this.post(card.group(), Map.of("reg_number", "10001", "issue_date", "2026-07-03", "honors", "0"));
        this.post(card.group() + "/duplicate",
            Map.of("kind", "supplement", "reg_number", "10020", "issue_date", "2026-09-01"));
        final String cardPage = this.get(card.group()).body();
        assertTrue(
            cardPage.contains("снято «с отличием» вопреки расчёту по п. 27")
                && cardPage.contains("<td>дубликат приложения</td><td>10020</td><td>2026-09-01</td>"),
            "The operator's decision on honors went without a word, or the duplicate was not issued: " + cardPage
        );
        assertEquals(
            400, this.post(card.group(), Map.of("reg_number", "10010", "issue_date", "2026-07-03")).statusCode(),
            "The number of another graduate was taken"
        );
    }

    @Test
    void cannotPrintOnBlankBeforeDocumentIsReady() throws Exception {
        assumeTrue(Fonts.serif("").isPresent(), "No Cyrillic serif font on this machine");
        final String graduation = this.confirm(this.upload(
            Books.statement(studied("Андреев А. А."), studied("Борисов Б. Б.")), info(true)
        ));
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        final String print = graduation.replace("graduations/", "print/") + "/supplement.pdf";
        final HttpResponse<String> untemplated = this.get(print + "?sample=1");
        assertTrue(untemplated.statusCode() == 400 && untemplated.body().contains("Нет шаблона «Приложение к диплому»"),
            "A supplement printed without its template: " + untemplated.body());
        assertEquals(302, this.template("supplement", "03", Templates.gzip(Templates.supplement())).statusCode(),
            "The template was not taken");
        assertTrue(this.get("blanks").body().contains("2 страницы; замечаний нет"),
            "The template uploaded is not on the page of blanks, or has problems");
        final HttpResponse<String> unready = this.get(print);
        assertTrue(unready.statusCode() == 400 && unready.body().contains("нет регистрационного номера"),
            "A supplement without its number printed for the blank: " + unready.body());
        final HttpResponse<byte[]> sample = this.bytes(print + "?sample=1");
        assertTrue(
            sample.statusCode() == 200 && sample.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"),
            "The sample of an unfinished supplement was refused"
        );
        this.post("organization", Map.of(
            "full_name", "университет", "locality", "г. Йошкар-Ола", "head_last_name", "Петров",
            "head_first_name", "Пётр", "head_middle_name", "Петрович"
        ));
        this.post(graduation + "/numbers", Map.of("numbers", "10001-10002", "issue_date", "2026-07-03"));
        final HttpResponse<byte[]> pdf = this.bytes(print);
        assertEquals(200, pdf.statusCode(), "The supplements of a graduation ready to print were refused: "
            + new String(pdf.body(), StandardCharsets.UTF_8));
        try (PDDocument document = Loader.loadPDF(pdf.body())) {
            final String text = new PDFTextStripper().getText(document);
            assertTrue(
                document.getNumberOfPages() == 4 && text.contains("Андреев") && text.contains("10002")
                    && text.contains("Производственная практика (преддипломная практика)")
                    && text.contains("Математика (курсовая работа)") && !text.contains("ОБРАЗЕЦ"),
                "The supplements of the two graduates are not two sides each with their data, course works in the table "
                    + "of a template without a band for them: " + text
            );
        }
        final HttpResponse<String> diploma = this.get(graduation.replace("graduations/", "print/") + "/diploma.pdf");
        assertTrue(diploma.statusCode() == 400 && diploma.body().contains("Нет шаблона «Диплом»"),
            "A diploma printed from the template of the supplement");
    }

    @Test
    void cannotLoseCorrectionOfCardOnNextLoad() throws Exception {
        // the practice of the first graduate is not in the statement at all
        final Object[] early = student("Андреев А. А.",
            row("Математика", 252, null, 5, null), row("Математика", 36, null, null, 4), row("Физика", 144, 5, null, null),
            row("Теория игр", 72, "V", null, null));
        final byte[] statement = Books.statement(early, studied("Борисов Б. Б."));
        final String graduation = this.confirm(this.upload(statement, info(true)));
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        assertEquals(400, this.get(graduation + "/xml").statusCode(), "The XML went out without the practice grade");
        final Matcher link = Pattern.compile("graduations/[0-9a-f-]{36}/graduates/[0-9a-f-]{36}")
            .matcher(this.get(graduation).body());
        assertTrue(link.find(), "No card of a graduate on the page");
        final String card = link.group();
        final Map<String, String> form = selects(this.get(card).body());
        assertEquals("", form.get("r.10.d.grade"), "The card offered no grade for the practice the statement lacks");
        form.put("r.10.d.grade", "5");
        form.put("gek_protocol", "7");
        assertEquals(302, this.post(card + "/edit", form).statusCode(), "The corrections were not taken");
        final String corrected = this.get(card).body();
        assertTrue(
            corrected.contains("в файлах: «3»") && corrected.contains("нет в ведомости, оценка введена вручную"),
            "The card does not show what the files have beside the corrections: " + corrected
        );
        this.confirm(this.upload(statement, info(true)));
        final HttpResponse<String> xml = this.get(graduation + "/xml");
        assertTrue(
            xml.statusCode() == 200 && xml.body().contains("<НомерПротоколаГэк>7</НомерПротоколаГэк>")
                && xml.body().split("<Практика><Наименование>Производственная практика \\(преддипломная практика\\)"
                    + "</Наименование><Оценка>5</Оценка><ЗачЕд>6</ЗачЕд></Практика>").length == 3,
            "The corrections of the card did not reach the XML, or were lost when the group was loaded again: " + xml.body()
        );
        assertTrue(
            this.get(graduation).body().contains("Андреев Андрей Андреевич: «Производственная практика (преддипломная практика)»: "
                + "оценка: пусто → «5»"),
            "The correction is not in the journal of the graduation"
        );
        final Matcher revert = Pattern.compile("name=\"revert\" value=\"([0-9a-f-]{36})\"").matcher(this.get(card).body());
        assertTrue(revert.find(), "A correction cannot be taken off");
        this.post(card + "/edit", Map.of("revert", revert.group(1)));
        assertTrue(revert.find(), "Only one of the two corrections can be taken off");
        this.post(card + "/edit", Map.of("revert", revert.group(1)));
        assertEquals(400, this.get(graduation + "/xml").statusCode(), "The corrections taken off still hold");
        final Map<String, String> wrong = new LinkedHashMap<>(Map.of("thesis_grade", "8"));
        final HttpResponse<String> refused = this.post(card + "/edit", wrong);
        assertTrue(refused.statusCode() == 400 && refused.body().contains("нужен код оценки 2–7"),
            "A grade that is not a code was taken: " + refused.body());
    }

    @Test
    void cannotExportGraduateWithoutStateExam() throws Exception {
        final String graduation = this.confirm(this.upload(
            Books.statement(studied("Андреев А. А."), studied("Борисов Б. Б.")), info(false)
        ));
        this.post(graduation, Map.of("chairman", "Председатель П. П."));
        final HttpResponse<String> xml = this.get(graduation + "/xml");
        assertTrue(
            xml.statusCode() == 400 && xml.body().contains("нет оценки за государственный экзамен"),
            "The XML went out without the state exam the plan has"
        );
    }

    @Test
    void cannotConfirmWhileGraduateHasNoSheet() throws Exception {
        final String match = this.upload(
            Books.statement(studied("Андреев А. А.")), info(true)
        );
        final String page = this.get(match).body();
        assertTrue(
            page.contains("нет в ведомости: 1") && !page.contains("value=\"confirm\""),
            "A graduate without a statement sheet could be confirmed"
        );
    }

    /** Uploads the files; the address of the matching page. */
    private String upload(final byte[] statement, final byte[] info) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"group\"\r\n\r\nИТС-41\r\n")
            .getBytes(StandardCharsets.UTF_8));
        part(body, "statement", "Ведомость.xlsx", statement);
        part(body, "info", "Сведения.xlsx", info);
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        final HttpResponse<String> response = this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + "graduations/new"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(302, response.statusCode(), "The files were not taken: " + response.body());
        final Matcher id = ID.matcher(response.headers().firstValue("Location").orElse(""));
        assertTrue(id.find(), "No graduation in the answer");
        return "graduations/" + id.group(1) + "/match";
    }

    /** Confirms the matching as the page has it; the address of the graduation. */
    private String confirm(final String match) throws Exception {
        final Map<String, String> fields = selects(this.get(match).body());
        fields.put("action", "confirm");
        final HttpResponse<String> response = this.post(match, fields);
        assertEquals(302, response.statusCode(), "The graduation was not confirmed: " + response.body());
        final Matcher id = ID.matcher(response.headers().firstValue("Location").orElse(""));
        assertTrue(id.find(), "No graduation in the answer");
        return "graduations/" + id.group(1);
    }

    /** The selects of a page with their chosen options, as a browser sends them. */
    private static Map<String, String> selects(final String page) {
        final Map<String, String> fields = new LinkedHashMap<>();
        final Matcher select = SELECT.matcher(page);
        while (select.find()) {
            final Matcher option = OPTION.matcher(select.group(2));
            String value = null;
            while (option.find()) {
                if (value == null || option.group(2) != null) {
                    value = option.group(1);
                }
                if (option.group(2) != null) {
                    break;
                }
            }
            fields.put(unescape(select.group(1)), unescape(value));
        }
        return fields;
    }

    private static String unescape(final String text) {
        return text.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&");
    }

    private static void part(
        final ByteArrayOutputStream body, final String name, final String fileName, final byte[] content
    ) throws Exception {
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\""
            + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(final String path, final Map<String, String> fields) throws Exception {
        final List<String> pairs = new ArrayList<>();
        for (final Map.Entry<String, String> field : fields.entrySet()) {
            pairs.add(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
        }
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs))).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    /** Uploads a template of the blanks. */
    private HttpResponse<String> template(final String kind, final String level, final byte[] content) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (final String[] field : new String[][] {{"kind", kind}, {"level", level}}) {
            body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + field[0] + "\"\r\n\r\n" + field[1]
                + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        part(body, "template", "Приложение к диплому бакалавра.fr3", content);
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + "blanks/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private HttpResponse<byte[]> bytes(final String path) throws Exception {
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + path)).build(), HttpResponse.BodyHandlers.ofByteArray()
        );
    }

    private HttpResponse<String> get(final String path) throws Exception {
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + path)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }
}
