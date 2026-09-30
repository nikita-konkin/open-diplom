package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanTitle;
import org.opendiplom.plans.PlanTotals;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;

/**
 * Credits (з.е.) of curriculum elements.
 *
 * <p>The total is in «Объем частей ОП в зачетных единицах» → «Всего»
 * (the format of «Планы») or «Трудоемкость в зачетных единицах» → «всего»
 * (older plans). A statement cannot give it: it lists a discipline once per
 * semester (B-31).
 *
 * <p>The plan comes as a workbook or as a PDF saved from «Планы»; a scan has
 * no text to read.
 */
public final class Curriculum {
    private static final List<String> NAME_TITLES =
        Arrays.asList("структура оп", "наименование дисциплин");
    private static final List<String> CREDIT_TITLES = Arrays.asList(
        "объем частей оп в зачетных единицах", "трудоемкость в зачетных единицах"
    );
    private static final int HEADER_ROWS = 60;
    private static final String PROGRAM = "объем образовательной программы";
    /** Columns to look through for the five hours of the program total. */
    private static final int HOURS_SPAN = 30;
    /** Rows below the titles to look through for the row of column numbers. */
    private static final int NUMBERS_SPAN = 15;
    private static final int NEAR_KEY = 10;

    private final Map<String, Double> credits;
    private final String sheet;
    private final String origin;
    private final PlanTitle title;
    private final List<PlanRow> rows;
    private final PlanTotals totals;

    private Curriculum(
        final Map<String, Double> credits, final String sheet, final String origin, final PlanTitle title,
        final List<PlanRow> rows
    ) {
        this.credits = Collections.unmodifiableMap(credits);
        this.sheet = sheet;
        this.origin = origin;
        this.title = title;
        this.rows = Collections.unmodifiableList(rows);
        this.totals = PlanTotals.of(rows);
    }

    /**
     * Credits of the first sheet that has both columns, or of the table
     * «План учебного процесса» of a PDF.
     *
     * @throws WorkbookException the file is neither a workbook nor a PDF with the table,
     *     or has no credits columns
     */
    public static Curriculum read(final byte[] content) throws WorkbookException {
        if (PlanPdf.pdf(content)) {
            return pdf(PlanPdf.read(content));
        }
        for (final Sheet sheet : Workbooks.read(content, "Учебный план")) {
            int[] name = null;
            int[] credit = null;
            for (int row = 0; row < Math.min(HEADER_ROWS, sheet.rows().size()); ++row) {
                for (int column = 0; column < sheet.width(); ++column) {
                    final Object value = sheet.cell(row, column);
                    if (!(value instanceof String)) {
                        continue;
                    }
                    final String text = title(value);
                    if (name == null && NAME_TITLES.contains(text)) {
                        name = new int[] {row, column};
                    }
                    if (credit == null && CREDIT_TITLES.stream().anyMatch(text::startsWith)) {
                        credit = new int[] {row, column};
                    }
                }
            }
            if (name == null || credit == null) {
                continue;
            }
            final Map<String, Double> credits = new LinkedHashMap<>();
            for (int row = Math.max(name[0], credit[0]) + 1; row < sheet.rows().size(); ++row) {
                final Object element = sheet.cell(row, name[1]);
                final Double value = Cells.number(sheet.cell(row, credit[1]));
                if (element instanceof String && !((String) element).strip().isEmpty() && value != null) {
                    credits.merge(nameKey(element), value, Double::sum);
                }
            }
            if (!credits.isEmpty()) {
                final List<String> pieces = new ArrayList<>();
                for (int row = 0; row < Math.min(name[0], credit[0]); ++row) {
                    for (int column = 0; column < sheet.width(); ++column) {
                        if (sheet.cell(row, column) instanceof String) {
                            pieces.add((String) sheet.cell(row, column));
                        }
                    }
                }
                return new Curriculum(
                    credits, sheet.name(), "лист «" + sheet.name() + "»", PlanTitle.of(pieces),
                    rows(sheet, Math.max(name[0], credit[0]) + 1, name[1], credit[1])
                );
            }
        }
        throw new WorkbookException(
            "Учебный план: не найдены колонки «Структура ОП» (или «Наименование "
                + "дисциплин») и «Объем частей ОП в зачетных единицах» (или «Трудоемкость "
                + "в зачетных единицах»)"
        );
    }

    /**
     * Rows of a sheet: blocks and the total are in the index column, and the
     * program total has its titles in one row and the numbers in the next.
     *
     * <p>Under the titles «Планы» numbers the columns; the forms of control
     * are the last numbered columns before the credits, the credits and the
     * hours are the numbered columns under their titles. A sheet without the
     * numbers gives the total credits and the first hours found.
     */
    private static List<PlanRow> rows(final Sheet sheet, final int start, final int name, final int credit) {
        int hours = -1;
        for (int row = 0; row < Math.min(start, sheet.rows().size()) && hours < 0; ++row) {
            for (int column = 0; column < sheet.width(); ++column) {
                final Object value = sheet.cell(row, column);
                if (value instanceof String && title(value).contains("в часах")
                    && (title(value).startsWith("объем") || title(value).startsWith("трудоемкость"))) {
                    hours = column;
                    break;
                }
            }
        }
        int first = start;
        List<Integer> controls = Collections.emptyList();
        List<Integer> credits = Collections.singletonList(credit);
        List<Integer> hourColumns = Collections.emptyList();
        for (int row = start; row < Math.min(start + NUMBERS_SPAN, sheet.rows().size()); ++row) {
            if (Double.valueOf(1).equals(Cells.number(sheet.cell(row, 0)))
                && Double.valueOf(2).equals(Cells.number(sheet.cell(row, name)))) {
                final List<Integer> numbered = new ArrayList<>();
                for (int column = 0; column < sheet.width(); ++column) {
                    if (Cells.number(sheet.cell(row, column)) != null) {
                        numbered.add(column);
                    }
                }
                controls = last(numbered, name, credit, PlanRow.CONTROLS);
                credits = first(numbered, credit, hours < 0 ? sheet.width() : hours, PlanRow.CREDITS);
                hourColumns = hours < 0 ? hourColumns : first(numbered, hours, sheet.width(), PlanRow.HOURS);
                first = row + 1;
                break;
            }
        }
        final List<PlanRow> rows = new ArrayList<>();
        for (int row = first; row < sheet.rows().size(); ++row) {
            final String label = label(sheet, row, name);
            if (label.isEmpty()) {
                continue;
            }
            final String element = Cells.text(sheet.cell(row, name));
            final String index = element.isEmpty() ? "" : index(sheet, row, name);
            final int numbers = title(label).contains(PROGRAM) && Cells.number(sheet.cell(row, credit)) == null
                && row + 1 < sheet.rows().size() && label(sheet, row + 1, name).isEmpty() ? row + 1 : row;
            final List<String> forms = new ArrayList<>();
            for (final int column : index.isEmpty() ? Collections.<Integer>emptyList() : controls) {
                forms.add(PlanRow.semesters(sheet.cell(numbers, column)));
            }
            final List<Double> units = new ArrayList<>();
            for (final int column : credits) {
                units.add(Cells.number(sheet.cell(numbers, column)));
            }
            final List<Double> values = new ArrayList<>();
            if (hourColumns.isEmpty()) {
                for (int column = hours; hours >= 0 && column < hours + HOURS_SPAN
                    && values.size() < PlanRow.HOURS; ++column) {
                    final Double value = Cells.number(sheet.cell(numbers, column));
                    if (value != null) {
                        values.add(value);
                    }
                }
            } else {
                for (final int column : hourColumns) {
                    values.add(Cells.number(sheet.cell(numbers, column)));
                }
            }
            rows.add(new PlanRow(index, element.isEmpty() ? label : element, forms, units, values));
            // below the total «Планы» counts hours per week, which are not rows of the plan
            if (title(label).contains(PROGRAM)) {
                break;
            }
        }
        return rows;
    }

    /** Up to a count of the numbered columns in [from, to), the first ones. */
    private static List<Integer> first(final List<Integer> numbered, final int from, final int to, final int count) {
        final List<Integer> found = new ArrayList<>();
        for (final int column : numbered) {
            if (from <= column && column < to && found.size() < count) {
                found.add(column);
            }
        }
        return found;
    }

    /** Up to a count of the numbered columns strictly between two, the last ones. */
    private static List<Integer> last(final List<Integer> numbered, final int after, final int before, final int count) {
        final List<Integer> found = new ArrayList<>();
        for (final int column : numbered) {
            if (after < column && column < before) {
                found.add(column);
            }
        }
        return found.subList(Math.max(0, found.size() - count), found.size());
    }

    /** The first text of a row up to the name column: an index, a name, a block or the total. */
    private static String label(final Sheet sheet, final int row, final int name) {
        for (int column = 0; column <= name; ++column) {
            final Object value = sheet.cell(row, column);
            if (value instanceof String && !((String) value).isBlank()) {
                return Cells.collapse((String) value);
            }
        }
        return "";
    }

    /** The index left of the name: «Б1.О.01», or «1» of a facultative typed as a number. */
    private static String index(final Sheet sheet, final int row, final int name) {
        for (int column = 0; column < name; ++column) {
            final String text = Cells.text(sheet.cell(row, column));
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static Curriculum pdf(final PlanPdf plan) throws WorkbookException {
        final Map<String, Double> credits = new LinkedHashMap<>();
        for (final PlanRow element : plan.elements()) {
            // block totals have no index and are not in the name column of a workbook
            if (element.credits() != null && (!plan.indexed() || !element.index().isEmpty())) {
                credits.merge(nameKey(element.name()), element.credits(), Double::sum);
            }
        }
        if (credits.isEmpty()) {
            throw new WorkbookException(
                "Учебный план: в таблице PDF нет ни одного значения в колонке «в зачетных единицах»"
            );
        }
        final String page = "страница " + plan.page();
        return new Curriculum(credits, page, "PDF, " + page, PlanTitle.of(plan.title()), plan.elements());
    }

    /** Sheet the credits were read from; for a PDF, its page. */
    public String sheet() {
        return this.sheet;
    }

    /** Where the credits were read from, for the operator: «лист «…»» or «PDF, страница N». */
    public String origin() {
        return this.origin;
    }

    /** Direction, profile and the rest of the title above the tables. */
    public PlanTitle title() {
        return this.title;
    }

    /** Rows of the table in the order printed: blocks, parts, elements and the program total. */
    public List<PlanRow> rows() {
        return this.rows;
    }

    /** Volumes of the program, practices, attestation and contact hours from the totals. */
    public PlanTotals totals() {
        return this.totals;
    }

    /** Credits by name key; a name listed twice (a practice split between blocks) has the sum. */
    public Map<String, Double> credits() {
        return this.credits;
    }

    /**
     * Credits of the first name found: exactly, or as the only plan element
     * whose name contains it or is contained in it.
     *
     * @return credits, or {@code null} when no name is in the plan
     */
    public Double find(final List<String> names) {
        final List<String> keys = new ArrayList<>();
        for (final String name : names) {
            if (name != null && !name.strip().isEmpty()) {
                keys.add(nameKey(name));
            }
        }
        for (final String key : keys) {
            if (this.credits.containsKey(key)) {
                return this.credits.get(key);
            }
        }
        for (final String key : keys) {
            if (key.length() < NEAR_KEY) {
                continue;
            }
            final List<String> near = new ArrayList<>();
            for (final String planned : this.credits.keySet()) {
                if (planned.contains(key) || planned.length() >= NEAR_KEY && key.contains(planned)) {
                    near.add(planned);
                }
            }
            if (near.size() == 1) {
                return this.credits.get(near.get(0));
            }
        }
        return null;
    }

    /** Name reduced for comparison: case, ё, punctuation and spaces. */
    public static String nameKey(final Object name) {
        return PlanRow.key(Cells.raw(name));
    }

    private static String title(final Object value) {
        return Cells.collapse(((String) value).toLowerCase(Locale.ROOT).replace('ё', 'е'));
    }
}
