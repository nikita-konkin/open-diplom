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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.Settings;
import org.opendiplom.storage.Database;

/** Loading, saving, correcting and deriving plans through real HTTP. */
final class PlansPageTest {
    private static final String BOUNDARY = "----opendiplom";
    private static final Pattern INPUT = Pattern.compile(
        "<input type=\"(?:text|hidden)\" name=\"([^\"]+)\" value=\"([^\"]*)\""
    );
    private static final String DIRECTION =
        "НАПРАВЛЕНИЕ ПОДГОТОВКИ 11.03.02 ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ";
    private static final String PROFILE = "Профиль: (11) \"Интеллектуальные телекоммуникационные системы и сети\"";

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
        this.server = new WebServer(settings, Database.open(settings.databaseUrl()));
        this.address = this.server.start();
    }

    @AfterEach
    void stop() throws Exception {
        this.server.stop();
        System.clearProperty("opendiplom.data");
        System.clearProperty("opendiplom.port");
    }

    @Test
    void cannotSavePlanBeforeItsYearIsGiven() throws Exception {
        final Map<String, String> fields = inputs(this.upload(Plans.workbook(Plans.bachelor(), DIRECTION, PROFILE, "Форма обучения - Очная")).body());
        final HttpResponse<String> refused = this.post("plans/save", fields);
        assertTrue(
            refused.statusCode() == 200 && refused.body().contains("год набора"),
            "A plan without the admission year was saved or the operator was not told why not"
        );
        fields.put("year", "2021");
        final HttpResponse<String> saved = this.post("plans/save", fields);
        assertTrue(
            saved.statusCode() == 303 || saved.statusCode() == 302,
            "A plan with the year given was not saved: " + saved.statusCode()
        );
        assertTrue(this.get("plans").body().contains("11.03.02"), "The saved plan is not in the list");
    }

    @Test
    void cannotMakeNewEditionOfSameFile() throws Exception {
        this.save(Plans.bachelor());
        assertTrue(
            this.saveResponse(Plans.bachelor()).headers().firstValue("Location").orElse("").endsWith("?unchanged=1"),
            "The same file loaded again became a new edition"
        );
    }

    @Test
    void cannotDropCellsOfLongPlanForm() throws Exception {
        final String id = this.save(Plans.bachelor());
        final Map<String, String> fields = new LinkedHashMap<>();
        // past the limit Tomcat drops the rest of the fields silently
        for (int filler = 0; filler < 1500; ++filler) {
            fields.put("x" + filler, "");
        }
        fields.putAll(inputs(this.get("plans/" + id + "/edit").body()));
        final String row = row(fields, Plans.PRACTICE);
        fields.put(row + "u0", "9");
        fields.put("action", "check");
        assertTrue(
            this.post("plans/" + id + "/edit", fields).body().contains("з.е.: 6 → 9"),
            "Cells of a form with more than a thousand fields were lost"
        );
    }

    @Test
    void cannotSaveBrokenSumsUnlessOperatorInsists() throws Exception {
        final String id = this.save(Plans.bachelor());
        final Map<String, String> fields = inputs(this.get("plans/" + id + "/edit").body());
        fields.put(row(fields, Plans.PRACTICE) + "u0", "9");
        fields.put("action", "save");
        final HttpResponse<String> refused = this.post("plans/" + id + "/edit", fields);
        assertTrue(
            refused.statusCode() == 200 && refused.body().contains("Суммы не сходятся"),
            "A plan whose sums do not hold was saved without the operator insisting"
        );
        fields.put("force", "1");
        assertEquals(302, this.post("plans/" + id + "/edit", fields).statusCode(), "The operator could not insist");
    }

    @Test
    void cannotCarryContactHoursToPlanOfOtherForm() throws Exception {
        final String id = this.save(Plans.bachelor());
        final Map<String, String> fields = inputs(this.get(
            "plans/" + id + "/derive?form=" + URLEncoder.encode("заочная", StandardCharsets.UTF_8) + "&year=2021"
        ).body());
        final String total = row(fields, "ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ");
        assertEquals(
            "заочная 2021 756  ", fields.get("form") + " " + fields.get("year") + " " + fields.get(total + "h0") + " "
                + fields.get(total + "h3") + " " + fields.get(total + "h4"),
            "A plan of another form kept the contact hours of the full-time one, or lost its title or hours"
        );
    }

    /** Saves a plan through the preview; the identifier of the edition. */
    private String save(final List<org.opendiplom.plans.PlanRow> rows) throws Exception {
        final String location = this.saveResponse(rows).headers().firstValue("Location").orElseThrow();
        final Matcher id = Pattern.compile("/plans/([0-9a-f-]{36})").matcher(location);
        assertTrue(id.find(), "No edition in " + location);
        return id.group(1);
    }

    private HttpResponse<String> saveResponse(final List<org.opendiplom.plans.PlanRow> rows) throws Exception {
        final Map<String, String> fields = inputs(
            this.upload(Plans.workbook(rows, DIRECTION, PROFILE, "Форма обучения - Очная", "2021 г.п.")).body()
        );
        return this.post("plans/save", fields);
    }

    /** The prefix «rN.» of the row with a name in a form. */
    private static String row(final Map<String, String> fields, final String name) {
        for (final Map.Entry<String, String> field : fields.entrySet()) {
            if (field.getKey().endsWith(".name") && field.getValue().equals(name)) {
                return field.getKey().substring(0, field.getKey().length() - "name".length());
            }
        }
        throw new AssertionError("No row «" + name + "» in the form");
    }

    private static Map<String, String> inputs(final String page) {
        final Map<String, String> fields = new LinkedHashMap<>();
        final Matcher input = INPUT.matcher(page);
        while (input.find()) {
            fields.put(input.group(1), input.group(2).replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"));
        }
        return fields;
    }

    private HttpResponse<String> upload(final byte[] plan) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"plan\"; filename=\"План.xlsx\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(plan);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + "plans/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
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

    private HttpResponse<String> get(final String path) throws Exception {
        return this.client.send(
            HttpRequest.newBuilder(URI.create(this.address + path)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }
}
