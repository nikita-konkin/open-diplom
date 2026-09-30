package org.opendiplom.plans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/**
 * A row of the table «План учебного процесса», from a workbook, a PDF or
 * typed in: an element, a part, a block heading or the program total.
 *
 * <p>The columns are those «Планы» numbers under the titles: 3–7 the
 * semesters of each form of control, 8–10 the credits, 11–15 the hours.
 */
public final class PlanRow {
    /** Экзамены, зачеты, зачеты с оценкой, курсовые проекты, курсовые работы. */
    public static final int CONTROLS = 5;
    public static final int EXAMS = 0;
    public static final int TESTS = 1;
    public static final int GRADED_TESTS = 2;
    public static final int COURSE_PROJECTS = 3;
    public static final int COURSE_WORKS = 4;
    /** Forms of control as the operator reads them, in the order of the columns. */
    public static final List<String> CONTROL_NAMES = Collections.unmodifiableList(Arrays.asList(
        "экзамены", "зачеты", "зачеты с оценкой", "курсовые проекты", "курсовые работы"
    ));
    /** Всего, экзамены, учебные занятия. */
    public static final int CREDITS = 3;
    /** Всего, экзамены, учебные занятия, контактная работа, самостоятельная работа. */
    public static final int HOURS = 5;
    public static final int CONTACT = 3;

    private static final Pattern SEMESTER = Pattern.compile("\\d+");
    private static final Pattern NOT_KEY = Pattern.compile("[^0-9a-zа-я]+");

    private final String index;
    private final String name;
    private final List<String> controls;
    private final List<Double> credits;
    private final List<Double> hours;

    /**
     * @param index «Б.1.1.1», «1» of a facultative, empty for blocks, headings and the total
     * @param controls semesters of each form of control, fewer values mean empty ones
     * @param credits credits columns, fewer values mean missing ones
     * @param hours hours columns, fewer values mean missing ones
     */
    public PlanRow(
        final String index, final String name, final List<String> controls, final List<Double> credits,
        final List<Double> hours
    ) {
        this.index = index == null ? "" : index.strip();
        this.name = name == null ? "" : name.strip();
        this.controls = padded(controls, CONTROLS, "");
        this.credits = padded(credits, CREDITS, null);
        this.hours = padded(hours, HOURS, null);
    }

    /** Semesters as «1,2,3» from «1, 2, 3», «2,4» or a number cell. */
    public static String semesters(final Object value) {
        if (value == null) {
            return "";
        }
        final String text = value instanceof Number
            && ((Number) value).doubleValue() == Math.rint(((Number) value).doubleValue())
            ? String.valueOf(((Number) value).longValue())
            : String.valueOf(value);
        final List<String> numbers = new ArrayList<>();
        final Matcher found = SEMESTER.matcher(text);
        while (found.find()) {
            numbers.add(String.valueOf(Integer.parseInt(found.group())));
        }
        return String.join(",", numbers);
    }

    /** Name reduced for comparison: case, ё, punctuation and spaces. */
    public static String key(final String name) {
        final String text = name == null ? "" : name.toLowerCase(Locale.ROOT).replace('ё', 'е');
        return Cells.collapse(NOT_KEY.matcher(text).replaceAll(" "));
    }

    public String index() {
        return this.index;
    }

    public String name() {
        return this.name;
    }

    /** «Всего» of the credits, the value a supplement prints. */
    public Double credits() {
        return this.credits.get(0);
    }

    public List<Double> creditColumns() {
        return this.credits;
    }

    public List<Double> hours() {
        return this.hours;
    }

    public List<String> controls() {
        return this.controls;
    }

    private static <T> List<T> padded(final List<T> values, final int size, final T empty) {
        final List<T> padded = new ArrayList<>(size);
        for (int position = 0; position < size; ++position) {
            padded.add(values != null && position < values.size() ? values.get(position) : empty);
        }
        return Collections.unmodifiableList(padded);
    }
}
