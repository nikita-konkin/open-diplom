package org.opendiplom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Books;
import org.opendiplom.Settings;
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
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"statement\"; filename=\""
            + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(this.address + "import"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
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
