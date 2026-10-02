package org.opendiplom.export;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.opendiplom.plans.PlanTotals;

/**
 * What the XML writes about the program, from a curriculum: the title gives
 * the direction, profile, qualification, form and term, the totals give the
 * volumes. The form of the 2026 files and printed supplements is kept: the
 * direction name as a sentence, the qualification and the form in lower case.
 */
public final class ProgramFields {
    /**
     * Normative full-time term by level (the middle of the code: 03 — бакалавриат,
     * 04 — магистратура). Supplements print it for part-time forms too, though
     * their plans say «4 года 6 месяцев» (decided by the owner 27.09.2026, B-35).
     */
    private static final Map<String, String> FULL_TIME_TERMS = Map.of("03", "4 года", "04", "2 года");

    private ProgramFields() {
    }

    /**
     * Field values by the names of the XML form; a value not found is left out.
     *
     * @param sources filled with where each value came from, or why it is missing
     */
    public static Map<String, String> of(
        final String code, final String direction, final String profile, final String qualification,
        final String studyForm, final String studyTerm, final PlanTotals totals, final Map<String, String> sources
    ) {
        final Map<String, String> values = new LinkedHashMap<>();
        final String header = "из шапки плана";
        put(values, sources, "direction",
            empty(code) || empty(direction) ? null : code + " " + sentence(direction), header);
        put(values, sources, "profile", profile, header);
        put(values, sources, "qualification", lower(qualification), header);
        put(values, sources, "study_form", lower(studyForm), header);
        final String normative = empty(code) || code.length() < 5 ? null : FULL_TIME_TERMS.get(code.substring(3, 5));
        if (normative != null && !empty(studyForm) && !"очная".equals(lower(studyForm))) {
            put(values, sources, "study_term", normative,
                "нормативный срок очной формы; в плане «" + studyTerm + "»");
        } else {
            put(values, sources, "study_term", studyTerm, header);
        }
        put(values, sources, "program_credits", number(totals.program()), "итог плана «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»");
        put(values, sources, "contact_hours",
            totals.contact() == null ? null : number(totals.contact()) + " ак.час",
            "итог плана, колонка «Контактная работа»");
        put(values, sources, "practice_credits", number(totals.practices()), "итог «Блок 2. Практика»");
        put(values, sources, "final_credits", number(totals.attestation()),
            "итог «Блок 3. Государственная итоговая аттестация»");
        sources.put("gek_chairman", "в учебном плане нет: впишите из приказа о составе ГЭК");
        return values;
    }

    /**
     * The program of the field values.
     *
     * @throws ValidationProblems the direction, profile and qualification disagree
     */
    public static Program program(final Map<String, String> values) throws ValidationProblems {
        return new Program.Builder()
            .direction(values.get("direction"))
            .profile(values.get("profile"))
            .qualification(values.get("qualification"))
            .studyForm(values.get("study_form"))
            .studyTerm(values.get("study_term"))
            .programCredits(values.get("program_credits"))
            .contactHours(values.get("contact_hours"))
            .practiceCredits(values.get("practice_credits"))
            .finalCredits(values.get("final_credits"))
            .gekChairman(values.get("gek_chairman"))
            .build();
    }

    /** «ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ» → «Информационные системы и технологии». */
    public static String sentence(final String text) {
        if (!text.equals(text.toUpperCase(Locale.ROOT))) {
            return text;
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static void put(
        final Map<String, String> values, final Map<String, String> sources, final String field,
        final String value, final String source
    ) {
        if (empty(value)) {
            sources.put(field, "в учебном плане не найдено: впишите");
        } else {
            values.put(field, value);
            sources.put(field, source);
        }
    }

    private static boolean empty(final String text) {
        return text == null || text.isEmpty();
    }

    private static String lower(final String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    private static String number(final Double value) {
        return value == null ? null : PlanTotals.number(value);
    }
}
