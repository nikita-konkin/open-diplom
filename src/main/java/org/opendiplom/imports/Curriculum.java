package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
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
    private static final int HOURS = 5;
    private static final int NEAR_KEY = 10;
    private static final Pattern NOT_KEY = Pattern.compile("[^0-9a-zа-я]+");

    private final Map<String, Double> credits;
    private final String sheet;
    private final String origin;
    private final PlanTitle title;
    private final PlanTotals totals;

    private Curriculum(
        final Map<String, Double> credits, final String sheet, final String origin, final PlanTitle title,
        final PlanTotals totals
    ) {
        this.credits = Collections.unmodifiableMap(credits);
        this.sheet = sheet;
        this.origin = origin;
        this.title = title;
        this.totals = totals;
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
                    PlanTotals.of(rows(sheet, Math.max(name[0], credit[0]) + 1, name[1], credit[1]))
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
     * Rows of a sheet for the totals: blocks are in the index column, and the
     * program total has its titles in one row and the numbers in the next.
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
        final List<PlanRow> rows = new ArrayList<>();
        for (int row = start; row < sheet.rows().size(); ++row) {
            final String label = label(sheet, row, name);
            if (label.isEmpty()) {
                continue;
            }
            final int numbers = title(label).contains(PROGRAM) && Cells.number(sheet.cell(row, credit)) == null
                && row + 1 < sheet.rows().size() && label(sheet, row + 1, name).isEmpty() ? row + 1 : row;
            final List<Double> values = new ArrayList<>();
            for (int column = hours; hours >= 0 && column < hours + HOURS_SPAN && values.size() < HOURS; ++column) {
                final Double value = Cells.number(sheet.cell(numbers, column));
                if (value != null) {
                    values.add(value);
                }
            }
            rows.add(new PlanRow("", label, Cells.number(sheet.cell(numbers, credit)), values));
        }
        return rows;
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

    private static Curriculum pdf(final PlanPdf plan) throws WorkbookException {
        final Map<String, Double> credits = new LinkedHashMap<>();
        for (final PlanRow element : plan.elements()) {
            // block totals have no index and are not in the name column of a workbook
            if (element.credits != null && (!plan.indexed() || !element.index.isEmpty())) {
                credits.merge(nameKey(element.name), element.credits, Double::sum);
            }
        }
        if (credits.isEmpty()) {
            throw new WorkbookException(
                "Учебный план: в таблице PDF нет ни одного значения в колонке «в зачетных единицах»"
            );
        }
        final String page = "страница " + plan.page();
        return new Curriculum(
            credits, page, "PDF, " + page, PlanTitle.of(plan.title()), PlanTotals.of(plan.elements())
        );
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
        final String text = Cells.raw(name).toLowerCase(Locale.ROOT).replace('ё', 'е');
        return Cells.collapse(NOT_KEY.matcher(text).replaceAll(" "));
    }

    private static String title(final Object value) {
        return Cells.collapse(((String) value).toLowerCase(Locale.ROOT).replace('ё', 'е'));
    }
}
