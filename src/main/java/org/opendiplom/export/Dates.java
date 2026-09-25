package org.opendiplom.export;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/** Dates from the student information file. */
final class Dates {
    private static final Pattern RUSSIAN = Pattern.compile("^\\d{1,2}\\.\\d{1,2}\\.\\d{4}$");
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern ISO_TIME =
        Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?$");
    private static final DateTimeFormatter RUSSIAN_FORMAT =
        DateTimeFormatter.ofPattern("d.M.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter ISO_FORMAT =
        DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private Dates() {
    }

    /**
     * An Excel date without its time, as ГГГГ-ММ-ДД; empty for an empty cell.
     *
     * <p>Text is read only as ДД.ММ.ГГГГ or ГГГГ-ММ-ДД: guessing would swap
     * the day and month of one of them.
     */
    static String dateOnly(final Object value, final String field) {
        if (Cells.blank(value)) {
            return "";
        }
        if (value instanceof Double) {
            throw new IllegalArgumentException(
                field + ": дата записана числом (" + Cells.text(value)
                    + "), установите для ячейки формат «Дата»"
            );
        }
        LocalDate date = null;
        if (value instanceof LocalDateTime) {
            date = ((LocalDateTime) value).toLocalDate();
        } else if (value instanceof String) {
            final String text = ((String) value).strip();
            try {
                if (RUSSIAN.matcher(text).matches()) {
                    date = LocalDate.parse(text, RUSSIAN_FORMAT);
                } else if (ISO.matcher(text).matches() || ISO_TIME.matcher(text).matches()) {
                    date = LocalDate.parse(text.substring(0, 10), ISO_FORMAT);
                    if (text.length() > 10) {
                        LocalDateTime.parse(text.replace(' ', 'T'));
                    }
                }
            } catch (final DateTimeParseException error) {
                date = null;
            }
        }
        if (date == null) {
            throw new IllegalArgumentException(
                field + ": не удалось распознать дату «" + Cells.raw(value) + "», ожидается ДД.ММ.ГГГГ"
            );
        }
        return date.toString();
    }

    /** A date within the given years. */
    static String checked(final Object value, final String field, final int first, final int last) {
        final String iso = dateOnly(value, field);
        if (iso.isEmpty()) {
            throw new IllegalArgumentException(field + ": не заполнено");
        }
        final int year = Integer.parseInt(iso.substring(0, 4));
        if (year < first || year > last) {
            throw new IllegalArgumentException(
                field + ": год " + year + " вне допустимого диапазона " + first + "–" + last
            );
        }
        return iso;
    }
}
