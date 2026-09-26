package org.opendiplom.printing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * A4 landscape sheet for calibrating a printer before printing on blanks.
 *
 * <p>Crosses are drawn 20 mm from the edges. The operator prints the sheet at
 * 100%, measures the distance from the left and top edges of the paper to the
 * centre of a cross, and enters 20 minus each measurement as the correction.
 */
public final class TestSheet {
    public static final float WIDTH = 297;
    public static final float HEIGHT = 210;
    public static final float MARGIN = 20;
    public static final float FIELD_X = 40;
    public static final float FIELD_Y = 60;
    public static final String FIELD = "Поле приложения: 40 мм от левого края, 60 мм от верхнего";
    private static final float CROSS = 6;

    private TestSheet() {
    }

    public static byte[] pdf(final Path font, final Calibration calibration) throws IOException {
        try (PdfSheet sheet = new PdfSheet(WIDTH, HEIGHT, font, calibration)) {
            for (final float x : new float[] {MARGIN, WIDTH - MARGIN}) {
                for (final float y : new float[] {MARGIN, HEIGHT - MARGIN}) {
                    cross(sheet, x, y);
                }
            }
            cross(sheet, WIDTH / 2, HEIGHT / 2);
            ruler(sheet, 40, 150);
            sheet.text("Открытый диплом — тестовый лист калибровки принтера", 40, 35, 14);
            sheet.text(FIELD, FIELD_X, FIELD_Y, 12);
            sheet.line(FIELD_X, FIELD_Y + 1, FIELD_X + 60, FIELD_Y + 1, 0.3f);
            final String[] steps = {
                "1. Печатайте в масштабе 100% (отключите «Вписать в страницу»), лист A4 альбомный.",
                "2. Измерьте от левого и верхнего края бумаги до центра левого верхнего креста.",
                "3. Поправка вправо = 20 − расстояние слева; поправка вниз = 20 − расстояние сверху.",
                String.format(
                    Locale.forLanguageTag("ru"),
                    "Текущая поправка этого принтера: вправо %.1f мм, вниз %.1f мм.",
                    calibration.dx(), calibration.dy()
                ),
            };
            for (int line = 0; line < steps.length; ++line) {
                sheet.text(steps[line], 40, 72 + line * 8, 11);
            }
            return sheet.pdf();
        }
    }

    private static void cross(final PdfSheet sheet, final float x, final float y) throws IOException {
        sheet.line(x - CROSS, y, x + CROSS, y, 0.3f);
        sheet.line(x, y - CROSS, x, y + CROSS, 0.3f);
    }

    /** A 100 mm ruler: a tick every millimetre, longer every 5 and 10. */
    private static void ruler(final PdfSheet sheet, final float x, final float y) throws IOException {
        sheet.line(x, y, x + 100, y, 0.3f);
        for (int mm = 0; mm <= 100; ++mm) {
            final float tick = mm % 10 == 0 ? 5 : mm % 5 == 0 ? 3.5f : 2;
            sheet.line(x + mm, y, x + mm, y - tick, 0.2f);
            if (mm % 10 == 0) {
                sheet.text(Integer.toString(mm / 10), x + mm - 1, y + 5, 8);
            }
        }
        sheet.text("см — проверка масштаба: 10 см линейки = 10 см на бумаге", x + 105, y, 9);
    }
}
