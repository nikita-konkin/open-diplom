package org.opendiplom.imports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/**
 * The title of a curriculum as «Планы» prints it above the tables, in Excel
 * and in PDF alike:
 *
 * <pre>
 * НАПРАВЛЕНИЕ ПОДГОТОВКИ  09.03.02  ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ
 * Профиль: (13) "Интеллектуальные информационные системы и технологии"
 * Форма обучения - Очная      Срок получения образования - 4 года
 * 2022 г.п.                   Квалификация - Бакалавр
 * </pre>
 *
 * <p>The direction and the profile are different fields of a supplement
 * (they were mixed up in 2026, B-25), so both are read apart.
 */
public final class PlanTitle {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String DASH = "\\s*[-–—:]\\s*";
    private static final Pattern DIRECTION = Pattern.compile(
        "(?:направление подготовки|специальность)\\s*:?\\s*(\\d{2}\\.\\d{2}\\.\\d{2})\\s*(.*)", FLAGS
    );
    private static final Pattern PROFILE = Pattern.compile(
        "^(?:профиль|направленность(?:\\s*\\(профиль\\))?|специализация|программа (?:магистратуры|"
            + "специалитета|бакалавриата|аспирантуры))\\s*:\\s*(?:\\(\\d+\\)\\s*)?(.+)",
        FLAGS
    );
    private static final Pattern FORM = Pattern.compile("^форма обучения" + DASH + "(.+)", FLAGS);
    private static final Pattern TERM = Pattern.compile("^срок получения образования" + DASH + "(.+)", FLAGS);
    private static final Pattern QUALIFICATION = Pattern.compile("^квалификация" + DASH + "(.+)", FLAGS);
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})\\s*г\\.\\s*п\\.?$", FLAGS);
    private static final Pattern QUOTES = Pattern.compile("^[\"«“„]+|[\"»”“]+$");

    private final Map<String, String> fields;

    private PlanTitle(final Map<String, String> fields) {
        this.fields = fields;
    }

    /**
     * The fields found among text pieces; the first piece that fits a field wins.
     *
     * @param pieces texts above the tables: cells of a sheet, or runs of a PDF line
     */
    public static PlanTitle of(final List<String> pieces) {
        String code = null;
        String direction = null;
        String profile = null;
        String form = null;
        String term = null;
        String qualification = null;
        String year = null;
        for (final String piece : pieces) {
            final String text = Cells.collapse(piece);
            Matcher found = DIRECTION.matcher(text);
            if (code == null && found.find()) {
                code = found.group(1);
                direction = found.group(2).strip();
            }
            profile = first(profile, PROFILE, text);
            form = first(form, FORM, text);
            term = first(term, TERM, text);
            qualification = first(qualification, QUALIFICATION, text);
            year = first(year, YEAR, text);
        }
        final Map<String, String> fields = new LinkedHashMap<>();
        put(fields, "Код направления", code);
        put(fields, "Направление подготовки", direction);
        put(fields, "Профиль", profile == null ? null : QUOTES.matcher(profile).replaceAll("").strip());
        put(fields, "Квалификация", qualification);
        put(fields, "Форма обучения", form);
        put(fields, "Срок обучения", term);
        put(fields, "Год набора", year);
        return new PlanTitle(fields);
    }

    /** Fields found, by their Russian names, in the order of a supplement. */
    public Map<String, String> fields() {
        return this.fields;
    }

    public String code() {
        return this.fields.get("Код направления");
    }

    public String direction() {
        return this.fields.get("Направление подготовки");
    }

    public String profile() {
        return this.fields.get("Профиль");
    }

    public String qualification() {
        return this.fields.get("Квалификация");
    }

    public String studyForm() {
        return this.fields.get("Форма обучения");
    }

    public String studyTerm() {
        return this.fields.get("Срок обучения");
    }

    public String year() {
        return this.fields.get("Год набора");
    }

    private static String first(final String found, final Pattern pattern, final String text) {
        if (found != null) {
            return found;
        }
        final Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    private static void put(final Map<String, String> fields, final String name, final String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(name, value);
        }
    }
}
