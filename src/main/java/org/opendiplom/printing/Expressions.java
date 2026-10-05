package org.opendiplom.printing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The expressions of CyberDiploma templates, in brackets inside a text:
 * fields of data sets and the few functions the templates use.
 *
 * <pre>[Студент."Фамилия"]
 * [DateInGenitive(&lt;Документ."Дата выдачи"&gt;)] года
 * [Copy(IntToStr(YearOf(&lt;Студент."Дата решения госкомиссии"&gt;)),3,2)]
 * [IIF(&lt;Документ."Диплом: С отличием"&gt;=1,'с отличием','')]
 * [Студент."Дата решения госкомиссии" #Ddd]</pre>
 *
 * <p>A field the data has not, or a function this does not know, prints
 * nothing and is reported, so a template of another blank shows at once what
 * it asks that is missing.
 */
public final class Expressions {
    private static final String[] MONTHS = {
        "января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября",
        "декабря",
    };

    private final Map<String, Object> fields;
    private final Set<String> problems;
    private String text;
    private int at;

    /**
     * @param fields values by {@code Набор."Поле"}: strings, numbers, booleans and dates
     * @param problems where what cannot be evaluated is reported
     */
    public Expressions(final Map<String, Object> fields, final Set<String> problems) {
        this.fields = fields;
        this.problems = problems;
    }

    /** The text with each bracketed expression replaced by its value. */
    public String text(final String template) {
        final StringBuilder out = new StringBuilder();
        int from = 0;
        while (true) {
            final int open = template.indexOf('[', from);
            final int close = open < 0 ? -1 : template.indexOf(']', open);
            if (close < 0) {
                out.append(template.substring(from));
                break;
            }
            out.append(template, from, open).append(this.value(template.substring(open + 1, close)));
            from = close + 1;
        }
        return out.toString().replace("\r\n", "\n");
    }

    /** One expression, without its brackets, as printed. */
    String value(final String expression) {
        String body = expression;
        String format = null;
        final int hash = expression.lastIndexOf(" #");
        if (hash > 0 && expression.indexOf('\'', hash) < 0) {
            body = expression.substring(0, hash);
            format = expression.substring(hash + 2);
        }
        this.text = body;
        this.at = 0;
        try {
            final Object value = this.expression();
            this.space();
            if (this.at < this.text.length()) {
                throw new IllegalArgumentException("лишнее после выражения: " + this.text.substring(this.at));
            }
            return format == null ? print(value) : formatted(value, format);
        } catch (final IllegalArgumentException | IndexOutOfBoundsException error) {
            this.problems.add("[" + expression + "]: " + error.getMessage());
            return "";
        }
    }

    private Object expression() {
        Object left = this.term();
        while (true) {
            this.space();
            if (this.peek("<>")) {
                this.at += 2;
                left = !equal(left, this.term());
            } else if (this.peek("=")) {
                ++this.at;
                left = equal(left, this.term());
            } else if (this.peek("+")) {
                ++this.at;
                final Object right = this.term();
                left = left instanceof Number && right instanceof Number
                    ? (Object) (((Number) left).longValue() + ((Number) right).longValue())
                    : print(left) + print(right);
            } else {
                return left;
            }
        }
    }

    private Object term() {
        this.space();
        final char first = this.text.charAt(this.at);
        if (first == '\'') {
            final int end = this.text.indexOf('\'', this.at + 1);
            final String value = this.text.substring(this.at + 1, end);
            this.at = end + 1;
            return value;
        }
        if (first == '(') {
            ++this.at;
            final Object value = this.expression();
            this.expect(')');
            return value;
        }
        if (first == '<') {
            final int end = this.text.indexOf('>', this.at);
            final String field = this.text.substring(this.at + 1, end);
            this.at = end + 1;
            return this.field(field);
        }
        if (Character.isDigit(first) || first == '-') {
            int end = this.at + 1;
            while (end < this.text.length() && Character.isDigit(this.text.charAt(end))) {
                ++end;
            }
            final long value = Long.parseLong(this.text.substring(this.at, end));
            this.at = end;
            return value;
        }
        int end = this.at;
        while (end < this.text.length() && (Character.isLetterOrDigit(this.text.charAt(end))
            || this.text.charAt(end) == '_')) {
            ++end;
        }
        final String name = this.text.substring(this.at, end);
        int next = end;
        while (next < this.text.length() && this.text.charAt(next) == ' ') {
            ++next;
        }
        if (next < this.text.length() && this.text.charAt(next) == '(') {
            this.at = next + 1;
            final List<Object> arguments = new ArrayList<>();
            this.space();
            if (!this.peek(")")) {
                arguments.add(this.expression());
                this.space();
                while (this.peek(",")) {
                    ++this.at;
                    arguments.add(this.expression());
                    this.space();
                }
            }
            this.expect(')');
            return call(name, arguments);
        }
        // a field without angle brackets: the data set name may have spaces
        final int dot = this.text.indexOf(".\"", this.at);
        final int quote = dot < 0 ? -1 : this.text.indexOf('"', dot + 2);
        if (quote < 0) {
            throw new IllegalArgumentException("непонятное выражение");
        }
        final String field = this.text.substring(this.at, quote + 1);
        this.at = quote + 1;
        return this.field(field);
    }

    private Object field(final String reference) {
        final int dot = reference.indexOf(".\"");
        if (dot < 0 || !reference.endsWith("\"")) {
            throw new IllegalArgumentException("не поле набора данных");
        }
        final String key = reference.substring(0, dot).strip() + ".\"" + reference.substring(dot + 2);
        if (this.fields.containsKey(key)) {
            final Object value = this.fields.get(key);
            return value == null ? "" : value;
        }
        // FastReport finds fields whatever their case: one template asks «наименование»
        for (final Map.Entry<String, Object> field : this.fields.entrySet()) {
            if (field.getKey().equalsIgnoreCase(key)) {
                return field.getValue() == null ? "" : field.getValue();
            }
        }
        throw new IllegalArgumentException("такого поля нет в данных");
    }

    private static Object call(final String name, final List<Object> arguments) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "dateingenitive":
                return date(arguments.get(0)) == null ? "" : date(arguments.get(0)).getDayOfMonth() + " "
                    + MONTHS[date(arguments.get(0)).getMonthValue() - 1] + " " + date(arguments.get(0)).getYear();
            case "monthingenitive":
                return date(arguments.get(0)) == null ? "" : MONTHS[date(arguments.get(0)).getMonthValue() - 1];
            case "yearof":
                return date(arguments.get(0)) == null ? (Object) "" : (long) date(arguments.get(0)).getYear();
            case "dayof":
                return date(arguments.get(0)) == null ? (Object) "" : (long) date(arguments.get(0)).getDayOfMonth();
            case "inttostr":
            case "trim":
                return print(arguments.get(0)).strip();
            case "uppercase":
                return print(arguments.get(0)).toUpperCase(Locale.ROOT);
            case "copy":
                final String value = print(arguments.get(0));
                final int start = (int) number(arguments.get(1)) - 1;
                final int length = (int) number(arguments.get(2));
                return start >= value.length() || start < 0 ? ""
                    : value.substring(start, Math.min(value.length(), start + length));
            case "iif":
                return truth(arguments.get(0)) ? arguments.get(1) : arguments.get(2);
            default:
                throw new IllegalArgumentException("функция " + name + " не поддерживается");
        }
    }

    /** A date in a Delphi format: d, dd, m, mm, yy, yyyy. */
    private static String formatted(final Object value, final String format) {
        if (!format.startsWith("D")) {
            return print(value);
        }
        final LocalDate date = date(value);
        if (date == null) {
            return "";
        }
        return format.substring(1).replace("yyyy", "\u0001").replace("yy", "\u0002").replace("dd", "\u0003")
            .replace("mm", "\u0004").replace("d", String.valueOf(date.getDayOfMonth()))
            .replace("m", String.valueOf(date.getMonthValue()))
            .replace("\u0001", String.valueOf(date.getYear()))
            .replace("\u0002", String.format("%02d", date.getYear() % 100))
            .replace("\u0003", String.format("%02d", date.getDayOfMonth()))
            .replace("\u0004", String.format("%02d", date.getMonthValue()));
    }

    private static LocalDate date(final Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof String && ((String) value).matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse((String) value);
        }
        return null;
    }

    private static boolean equal(final Object left, final Object right) {
        if (left instanceof Boolean || right instanceof Boolean) {
            return truth(left) == truth(right);
        }
        if (left instanceof Number && right instanceof Number) {
            return ((Number) left).doubleValue() == ((Number) right).doubleValue();
        }
        return print(left).equals(print(right));
    }

    private static boolean truth(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return !print(value).isEmpty();
    }

    private static double number(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(print(value).strip());
    }

    private static String print(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }
        return value == null ? "" : String.valueOf(value);
    }

    private void space() {
        while (this.at < this.text.length() && Character.isWhitespace(this.text.charAt(this.at))) {
            ++this.at;
        }
    }

    private boolean peek(final String token) {
        return this.text.startsWith(token, this.at);
    }

    private void expect(final char token) {
        this.space();
        if (this.at >= this.text.length() || this.text.charAt(this.at) != token) {
            throw new IllegalArgumentException("ожидалось «" + token + "»");
        }
        ++this.at;
    }
}
