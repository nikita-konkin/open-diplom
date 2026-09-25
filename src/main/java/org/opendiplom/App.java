package org.opendiplom;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Logger;
import org.opendiplom.storage.Database;
import org.opendiplom.web.WebServer;

/**
 * Starts the program: settings, database, web server and, on a single PC,
 * the browser.
 */
public final class App {
    private static final Logger LOG = Logger.getLogger(App.class.getName());

    private App() {
    }

    public static void main(final String... args) throws Exception {
        // Apache POI logs through Log4j API; without this it complains on every start
        System.setProperty(
            "log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory"
        );
        final Settings settings = Settings.load(Paths.get("").toAbsolutePath());
        Files.createDirectories(settings.dataFolder());
        final Database database = Database.open(settings.databaseUrl());
        final WebServer server = new WebServer(settings, database);
        final String address = server.start();
        final long started = System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
        LOG.info(String.format(
            Locale.ROOT, "Открытый диплом запущен за %d мс: %s (схема БД %d, режим %s)",
            started, address, database.version(), settings.desktop() ? "desktop" : "server"
        ));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (final Exception error) {
                LOG.warning("Остановка: " + error.getMessage());
            }
        }));
        if (settings.openBrowser()) {
            browse(address);
        }
    }

    private static void browse(final String address) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(address));
                return;
            }
            final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", address).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", address).start();
            } else {
                new ProcessBuilder("xdg-open", address).start();
            }
        } catch (final IOException | RuntimeException error) {
            LOG.info("Откройте в браузере: " + address);
        }
    }
}
