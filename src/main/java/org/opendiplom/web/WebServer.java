package org.opendiplom.web;

import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.opendiplom.Settings;
import org.opendiplom.storage.Database;

/**
 * Embedded Tomcat 10.1: the last servlet container line that runs on Java 11
 * and still gets security fixes (Jetty 9–11 no longer do, ADR-0007).
 */
public final class WebServer {
    private static final long MB = 1024L * 1024L;
    /**
     * A plan corrected by hand posts 17 fields a row, some 1200 for a
     * bachelor plan. Tomcat 10.1.60 takes 10000 by default; set here so that
     * an upgrade lowering the default does not cut long plans silently.
     */
    private static final int MAX_PARAMETERS = 10_000;

    private final Tomcat tomcat = new Tomcat();
    private final Connector connector = new Connector();
    private final String address;
    private final String contextPath;

    public WebServer(final Settings settings, final Database database) throws IOException {
        final Path base = Files.createDirectories(settings.dataFolder().resolve("web"));
        this.tomcat.setBaseDir(base.toString());
        this.address = settings.address();
        this.contextPath = settings.contextPath();
        this.connector.setPort(settings.port());
        this.connector.setProperty("address", settings.address());
        this.connector.setURIEncoding("UTF-8");
        this.connector.setMaxPostSize((int) (settings.maxUploadMb() * MB));
        this.connector.setMaxParameterCount(MAX_PARAMETERS);
        this.connector.setProperty("server", "open-diplom");
        this.tomcat.getService().addConnector(this.connector);
        this.tomcat.setConnector(this.connector);
        final Context context = this.tomcat.addContext(settings.contextPath(), base.toString());
        Html.root(settings.contextPath());
        context.setRequestCharacterEncoding("UTF-8");
        context.setResponseCharacterEncoding("UTF-8");
        final MultipartConfigElement uploads = new MultipartConfigElement(
            null, settings.maxUploadMb() * MB, settings.maxUploadMb() * MB, (int) MB
        );
        this.add(context, "/", new HomePage(settings, database), null);
        this.add(context, "/import", new ImportPage(database), uploads);
        this.add(
            context, "/plans/*", new PlansPage(database, settings.dataFolder().resolve("staging")), uploads
        );
        this.add(
            context, "/graduations/*",
            new GraduationsPage(database, settings.dataFolder().resolve("staging").resolve("graduations")), uploads
        );
        this.add(context, "/organization", new OrganizationPage(database), null);
        this.add(context, "/blanks/*", new BlanksPage(database, settings), uploads);
        this.add(context, "/print/*", new PrintPage(database, settings), null);
        this.add(context, "/xml", new XmlPage(), uploads);
        this.add(context, "/xml/plan", new XmlPlanPage(), uploads);
        this.add(context, "/test-sheet.pdf", new TestSheetPage(settings), null);
        this.add(context, "/health", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
                throws IOException {
                response.setContentType("text/plain; charset=utf-8");
                response.getWriter().write("ok");
            }
        }, null);
    }

    /** Starts listening; returns the address to open in a browser. */
    public String start() throws LifecycleException {
        this.tomcat.start();
        final String host = "0.0.0.0".equals(this.address) ? "127.0.0.1" : this.address;
        return "http://" + host + ':' + this.connector.getLocalPort() + this.contextPath + '/';
    }

    public void stop() throws LifecycleException {
        this.tomcat.stop();
        this.tomcat.destroy();
    }

    private void add(
        final Context context, final String path, final HttpServlet servlet,
        final MultipartConfigElement uploads
    ) {
        final String name = path.equals("/") ? "home" : path.substring(1).replace("/*", "");
        final Wrapper wrapper = Tomcat.addServlet(context, name, servlet);
        if (uploads != null) {
            wrapper.setMultipartConfigElement(uploads);
        }
        context.addServletMappingDecoded(path, name);
    }
}
