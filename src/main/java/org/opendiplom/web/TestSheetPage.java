package org.opendiplom.web;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.opendiplom.Settings;
import org.opendiplom.printing.Calibration;
import org.opendiplom.printing.Fonts;
import org.opendiplom.printing.TestSheet;

/** The printer calibration sheet as PDF. */
final class TestSheetPage extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String TITLE = "Тестовый лист";

    private final transient Settings settings;

    TestSheetPage(final Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException {
        final Optional<Path> font = Fonts.serif(this.settings.font());
        if (font.isEmpty()) {
            Responses.problem(
                response, TITLE,
                "Не найден шрифт с кириллицей (PT Astra Serif, Times New Roman или Liberation Serif). "
                    + "Установите один из них или укажите файл шрифта в настройке font."
            );
            return;
        }
        final Calibration calibration;
        try {
            calibration = new Calibration(millimetres(request, "dx"), millimetres(request, "dy"));
        } catch (final IllegalArgumentException error) {
            Responses.problem(response, TITLE, error.getMessage());
            return;
        }
        final byte[] pdf = TestSheet.pdf(font.get(), calibration);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"test-sheet.pdf\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
    }

    private static float millimetres(final HttpServletRequest request, final String name) {
        final String value = request.getParameter(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Float.parseFloat(value.strip().replace(',', '.'));
        } catch (final NumberFormatException error) {
            throw new IllegalArgumentException("Поправка «" + value + "» — не число миллиметров", error);
        }
    }
}
