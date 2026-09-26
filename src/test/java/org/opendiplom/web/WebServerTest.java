package org.opendiplom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Books;
import org.opendiplom.PlanPdfs;
import org.opendiplom.Settings;
import org.opendiplom.printing.Fonts;
import org.opendiplom.storage.Database;

/** Through real HTTP, as a browser sends it. */
final class WebServerTest {
    private static final String BOUNDARY = "----opendiplom";

    @TempDir
    Path folder;

    private WebServer server;
    private String address;

    @BeforeEach
    void start() throws Exception {
        System.setProperty("opendiplom.data", this.folder.toString());
        System.setProperty("opendiplom.port", "0");
        final Settings settings = Settings.load(this.folder);
        this.server = new WebServer(settings, Database.open(settings.databaseUrl()));
        this.address = this.server.start();
    }

    @AfterEach
    void stop() throws Exception {
        this.server.stop();
        System.clearProperty("opendiplom.data");
        System.clearProperty("opendiplom.port");
    }

    private HttpResponse<String> upload(final String fileName, final byte[] content) throws Exception {
        return this.upload(fileName, content, null);
    }

    private HttpResponse<String> upload(final String fileName, final byte[] content, final byte[] plan)
        throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(body, "statement", fileName, content);
        if (plan != null) {
            part(body, "curriculum", "План.xlsx", plan);
        }
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(this.address + "import"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private static void part(
        final ByteArrayOutputStream body, final String name, final String fileName, final byte[] content
    ) throws Exception {
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\""
            + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String home() throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(this.address)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();
    }

    @Test
    void cannotLoseCyrillicFileNameOfUpload() throws Exception {
        upload("Ведомость ИСТ-43.xlsx", Books.statement(student("Тестов Т. Т.", row("Математика", 108, null, 5, null))));
        assertTrue(home().contains("Ведомость ИСТ-43.xlsx"), "A Cyrillic file name was garbled on the way to the database");
    }

    @Test
    void cannotShowGradesUnderCreditsCountedFromHours() throws Exception {
        final String page = upload(
            "Ведомость.xlsx",
            Books.statement(student("Тестов Т. Т.", row("Математика", 108, null, 5, null))),
            Books.curriculum(Collections.singletonMap("Математика", 4))
        ).body();
        assertFalse(
            page.substring(page.indexOf("<h2>Оценки</h2>")).contains("Математика_дисциплина_3"),
            "The grades table kept the credits counted from hours instead of the curriculum ones"
        );
    }

    @Test
    void cannotRefuseCurriculumSavedToPdf() throws Exception {
        final Optional<Path> font = Fonts.serif(null);
        assumeTrue(font.isPresent(), "No Cyrillic serif font on this machine");
        final String page = upload(
            "Ведомость.xlsx",
            Books.statement(student("Тестов Т. Т.", row("Математика", 108, null, 5, null))),
            PlanPdfs.plan(font.get())
        ).body();
        assertTrue(
            page.contains("PDF, страница 1") && page.contains("ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ")
                && page.contains("Математика_дисциплина_16"),
            "A curriculum in PDF was not read, or its title and credits were not shown"
        );
    }

    @Test
    void cannotLeaveXmlFieldsEmptyWhenPlanHasThem() throws Exception {
        final Optional<Path> font = Fonts.serif(null);
        assumeTrue(font.isPresent(), "No Cyrillic serif font on this machine");
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(body, "curriculum", "План.pdf", PlanPdfs.plan(font.get()));
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        final String page = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(this.address + "xml/plan"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();
        // as the 2026 files and printed supplements have them
        for (final String value : new String[] {
            "09.03.02 Информационные системы и технологии", "Интеллектуальные информационные системы и технологии",
            "бакалавр", "очная", "4 года", "103", "1500 ак.час", "24", "9",
        }) {
            assertTrue(page.contains("value=\"" + value + "\""), "The field was not filled from the plan: " + value);
        }
        assertTrue(page.contains("value=\"\" required>"), "The chairman of the commission, absent from plans, got a value");
    }

    @Test
    void cannotHideWhyCurriculumWasNotRead() throws Exception {
        final String page = upload(
            "Ведомость.xlsx",
            Books.statement(student("Тестов Т. Т.", row("Математика", 108, null, 5, null))),
            PlanPdfs.scan()
        ).body();
        assertTrue(
            page.substring(0, page.indexOf("<h2>Зачётные единицы</h2>")).contains("похоже, это скан"),
            "The reason an uploaded plan was not read is not shown above the tables"
        );
    }

    @Test
    void cannotAnswerBadStatementWithServerError() throws Exception {
        assertEquals(
            400, upload("notes.txt", "не таблица".getBytes(StandardCharsets.UTF_8)).statusCode(),
            "A file that is not a workbook was not refused as the operator's mistake"
        );
    }

    @Test
    void cannotListenBeyondThisComputerInDesktopMode() {
        assertTrue(
            this.address.startsWith("http://127.0.0.1:"),
            "In desktop mode the program is reachable from the network: " + this.address
        );
    }
}
