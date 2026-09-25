package org.opendiplom.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;

/** Files and fields of a multipart form. */
final class Uploads {
    private final HttpServletRequest request;

    Uploads(final HttpServletRequest request) {
        this.request = request;
    }

    /** Content of an uploaded file, {@code null} when none was chosen. */
    byte[] file(final String name) throws IOException, ServletException {
        final Part part = this.request.getPart(name);
        if (part == null || part.getSize() == 0) {
            return null;
        }
        try (InputStream in = part.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /** Name of an uploaded file as the browser sent it, {@code null} when none. */
    String fileName(final String name) throws IOException, ServletException {
        final Part part = this.request.getPart(name);
        return part == null || part.getSize() == 0 ? null : part.getSubmittedFileName();
    }

    /** A text field, empty when absent. */
    String field(final String name) {
        final String value = this.request.getParameter(name);
        return value == null ? "" : value.strip();
    }
}
