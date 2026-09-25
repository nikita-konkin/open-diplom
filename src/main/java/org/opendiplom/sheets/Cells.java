package org.opendiplom.sheets;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cell values as text and numbers, with the same results as the Python
 * service this code replaces, so both can be compared on real data.
 */
public final class Cells {
    private static final Pattern NUMBER = Pattern.compile(
        "[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?"
    );
    private static final DateTimeFormatter TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private Cells() {
    }

    /** An empty cell: nothing, or a number that is not a number. */
    public static boolean missing(final Object value) {
        return value == null || value instanceof Double && ((Double) value).isNaN();
    }

    /** Empty in the wider sense of the XML rules: also the texts «nan», «none»… */
    public static boolean blank(final Object value) {
        if (missing(value)) {
            return true;
        }
        if (value instanceof String) {
            final String text = ((String) value).strip().toLowerCase(Locale.ROOT);
            return text.isEmpty() || "nan".equals(text) || "nat".equals(text)
                || "none".equals(text);
        }
        return false;
    }

    /** The value as the operator reads it: «3» for 3.0, spaces collapsed. */
    public static String text(final Object value) {
        if (blank(value)) {
            return "";
        }
        if (value instanceof Double) {
            final double number = (Double) value;
            if (number == Math.rint(number) && !Double.isInfinite(number)) {
                return Long.toString((long) number);
            }
        }
        return collapse(raw(value));
    }

    /** The value as Python's {@code str()} writes it. */
    public static String raw(final Object value) {
        if (value == null) {
            return "nan";
        }
        if (value instanceof Double) {
            final double number = (Double) value;
            if (Double.isNaN(number)) {
                return "nan";
            }
            if (number == Math.rint(number) && Math.abs(number) < 1e16) {
                return (long) number + ".0";
            }
            return Double.toString(number);
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "True" : "False";
        }
        if (value instanceof LocalDateTime) {
            return TIMESTAMP.format((LocalDateTime) value);
        }
        return value.toString();
    }

    /**
     * A number from a numeric cell or a text holding a plain number.
     *
     * @return the number, or {@code null} when the value is not a number
     */
    public static Double number(final Object value) {
        if (value instanceof Double) {
            return ((Double) value).isNaN() ? null : (Double) value;
        }
        if (value instanceof String) {
            final String text = ((String) value).strip();
            if (NUMBER.matcher(text).matches()) {
                return Double.valueOf(text);
            }
        }
        return null;
    }

    /** Words of a text split at any whitespace, as Python's {@code split()}. */
    public static List<String> words(final String text) {
        final List<String> words = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        for (int index = 0; index < text.length(); ++index) {
            final char letter = text.charAt(index);
            if (Character.isWhitespace(letter) || Character.isSpaceChar(letter)) {
                if (word.length() > 0) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(letter);
            }
        }
        if (word.length() > 0) {
            words.add(word.toString());
        }
        return words;
    }

    /** Text with whitespace runs collapsed to single spaces and trimmed. */
    public static String collapse(final String text) {
        return String.join(" ", words(text));
    }
}
