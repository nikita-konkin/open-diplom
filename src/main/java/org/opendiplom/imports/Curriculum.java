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
 */
public final class Curriculum {
    private static final List<String> NAME_TITLES =
        Arrays.asList("структура оп", "наименование дисциплин");
    private static final List<String> CREDIT_TITLES = Arrays.asList(
        "объем частей оп в зачетных единицах", "трудоемкость в зачетных единицах"
    );
    private static final int HEADER_ROWS = 60;
    private static final int NEAR_KEY = 10;
    private static final Pattern NOT_KEY = Pattern.compile("[^0-9a-zа-я]+");

    private final Map<String, Double> credits;
    private final String sheet;

    private Curriculum(final Map<String, Double> credits, final String sheet) {
        this.credits = Collections.unmodifiableMap(credits);
        this.sheet = sheet;
    }

    /**
     * Credits of the first sheet that has both columns.
     *
     * @throws WorkbookException the file is not a workbook or has no credits columns
     */
    public static Curriculum read(final byte[] content) throws WorkbookException {
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
                return new Curriculum(credits, sheet.name());
            }
        }
        throw new WorkbookException(
            "Учебный план: не найдены колонки «Структура ОП» (или «Наименование "
                + "дисциплин») и «Объем частей ОП в зачетных единицах» (или «Трудоемкость "
                + "в зачетных единицах»)"
        );
    }

    /** Sheet the credits were read from. */
    public String sheet() {
        return this.sheet;
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
