package org.opendiplom.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Writing pages and files back to the browser. */
final class Responses {
    private Responses() {
    }

    static void html(final HttpServletResponse response, final int status, final String page)
        throws IOException {
        response.setStatus(status);
        response.setContentType("text/html; charset=utf-8");
        response.getOutputStream().write(page.getBytes(StandardCharsets.UTF_8));
    }

    /** A message the operator can act on, with the form to go back to. */
    static void problem(final HttpServletResponse response, final String title, final String message)
        throws IOException {
        html(
            response, HttpServletResponse.SC_BAD_REQUEST,
            Html.page(title, "<h1>" + Html.escape(title) + "</h1><section class=\"error\">"
                + Html.escape(message) + "</section><p><a href=\"./\">Вернуться</a></p>")
        );
    }

    /** A file to save; the name may be Cyrillic (RFC 5987). */
    static void file(
        final HttpServletResponse response, final String type, final String name, final byte[] content
    ) throws IOException {
        response.setContentType(type);
        final String ascii = name.replaceAll("[^\\x20-\\x7e]", "_").replace("\"", "_");
        response.setHeader(
            "Content-Disposition",
            "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''"
                + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20")
        );
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
    }
}
