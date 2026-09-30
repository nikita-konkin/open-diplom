package org.opendiplom.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.Settings;
import org.opendiplom.storage.Database;

/** The application served by a reverse proxy under a prefix, as on a university server. */
final class ContextPathTest {
    private static final String BOUNDARY = "----opendiplom";
    private static final Pattern INPUT = Pattern.compile(
        "<input type=\"(?:text|hidden)\" name=\"([^\"]+)\" value=\"([^\"]*)\""
    );

    @TempDir
    Path folder;

    private WebServer server;
    private String root;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        System.setProperty("opendiplom.data", this.folder.toString());
        System.setProperty("opendiplom.port", "0");
        System.setProperty("opendiplom.context-path", "/open-diplom/");
        final Settings settings = Settings.load(this.folder);
        this.server = new WebServer(settings, Database.open(settings.databaseUrl()));
        this.root = this.server.start();
    }

    @AfterEach
    void stop() throws Exception {
        this.server.stop();
        System.clearProperty("opendiplom.data");
        System.clearProperty("opendiplom.port");
        System.clearProperty("opendiplom.context-path");
        Html.root("");
    }

    @Test
    void cannotLinkOutsideOfPrefix() throws Exception {
        assertTrue(this.root.endsWith("/open-diplom/"), "The address to open does not have the prefix: " + this.root);
        final String page = this.get(this.root).body();
        final String links = page.replace("<base href=\"/open-diplom/\">", "");
        assertTrue(
            page.contains("<base href=\"/open-diplom/\">") && links.contains("href=\"plans\"")
                && !links.contains("href=\"/") && !links.contains("action=\"/"),
            "A page links past the prefix the proxy serves it under"
        );
    }

    @Test
    void cannotRedirectOutsideOfPrefix() throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"plan\"; filename=\"План.xlsx\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Plans.workbook(
            Plans.bachelor(), "НАПРАВЛЕНИЕ ПОДГОТОВКИ 11.03.02 ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ",
            "Форма обучения - Очная", "2021 г.п."
        ));
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        final String preview = this.client.send(
            HttpRequest.newBuilder(URI.create(this.root + "plans/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        ).body();
        final List<String> pairs = new ArrayList<>();
        final Matcher input = INPUT.matcher(preview);
        while (input.find()) {
            pairs.add(input.group(1) + "=" + URLEncoder.encode(input.group(2), StandardCharsets.UTF_8));
        }
        final HttpResponse<String> saved = this.client.send(
            HttpRequest.newBuilder(URI.create(this.root + "plans/save"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs))).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        final String location = saved.headers().firstValue("Location").orElse("");
        assertTrue(
            location.matches("(https?://[^/]+)?/open-diplom/plans/[0-9a-f-]{36}"),
            "Saving a plan redirected past the prefix: " + saved.statusCode() + " " + location
        );
        assertEquals(
            200, this.get(URI.create(this.root).resolve(location).toString()).statusCode(),
            "The saved plan is not found under the prefix"
        );
    }

    private HttpResponse<String> get(final String address) throws Exception {
        return this.client.send(
            HttpRequest.newBuilder(URI.create(address)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }
}
