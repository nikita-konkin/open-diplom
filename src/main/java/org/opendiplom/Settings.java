package org.opendiplom;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Settings from {@code open-diplom.properties} in the working folder,
 * overridden by {@code -Dopendiplom.<name>=…}.
 *
 * <p>One build works in two modes (ADR-0001): {@code desktop} listens on
 * 127.0.0.1 only and keeps the data in a SQLite file next to the program;
 * {@code server} listens on the network.
 */
public final class Settings {
    public static final String FILE = "open-diplom.properties";

    private final Properties values;

    private Settings(final Properties values) {
        this.values = values;
    }

    public static Settings load(final Path folder) throws IOException {
        final Properties values = new Properties();
        final Path file = folder.resolve(FILE);
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                values.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        }
        for (final String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("opendiplom.")) {
                values.setProperty(name.substring("opendiplom.".length()), System.getProperty(name));
            }
        }
        return new Settings(values);
    }

    public boolean desktop() {
        return !"server".equals(this.values.getProperty("mode", "desktop"));
    }

    public String address() {
        return this.values.getProperty("address", this.desktop() ? "127.0.0.1" : "0.0.0.0");
    }

    /** Port; 0 picks any free one. */
    public int port() {
        return Integer.parseInt(this.values.getProperty("port", "8090"));
    }

    public Path dataFolder() {
        return Paths.get(this.values.getProperty("data", "data")).toAbsolutePath();
    }

    public String databaseUrl() {
        return this.values.getProperty(
            "database", "jdbc:sqlite:" + this.dataFolder().resolve("open-diplom.db")
        );
    }

    /**
     * The prefix a reverse proxy serves the application under: «» for the
     * root, «/open-diplom» for https://example.org/open-diplom/.
     */
    public String contextPath() {
        final String path = this.values.getProperty("context-path", "").strip().replaceAll("/+$", "");
        return path.isEmpty() || path.startsWith("/") ? path : "/" + path;
    }

    /** TrueType font for printing; empty to look for a known one. */
    public String font() {
        return this.values.getProperty("font", "");
    }

    /** Largest accepted upload, megabytes. */
    public int maxUploadMb() {
        return Integer.parseInt(this.values.getProperty("max-upload-mb", "20"));
    }

    public boolean openBrowser() {
        return this.desktop() && Boolean.parseBoolean(this.values.getProperty("open-browser", "true"));
    }
}
