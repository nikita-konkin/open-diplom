package org.opendiplom.plans;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/**
 * What a curriculum is known by: the program (direction code and profile),
 * the study form and the admission year, with the rest of its title. The
 * title of a file gives most of it; the operator confirms or fills it in.
 */
public final class PlanHeader {
    private static final Pattern CODE = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{2}");
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");

    private final String code;
    private final String direction;
    private final String profile;
    private final String qualification;
    private final String studyForm;
    private final String studyTerm;
    private final String year;

    public PlanHeader(
        final String code, final String direction, final String profile, final String qualification,
        final String studyForm, final String studyTerm, final String year
    ) {
        this.code = clean(code);
        this.direction = clean(direction);
        this.profile = clean(profile);
        this.qualification = clean(qualification);
        this.studyForm = clean(studyForm).toLowerCase(Locale.ROOT);
        this.studyTerm = clean(studyTerm);
        this.year = clean(year);
    }

    /** The fields found in the title of a plan, empty where none was found. */
    public static PlanHeader of(final PlanTitle title) {
        return new PlanHeader(
            title.code(), title.direction(), title.profile(), title.qualification(), title.studyForm(),
            title.studyTerm(), title.year()
        );
    }

    /** What must be filled in before the plan can be saved; empty when nothing. */
    public List<String> missing() {
        final List<String> missing = new ArrayList<>();
        if (!CODE.matcher(this.code).matches()) {
            missing.add("код направления вида 11.03.02");
        }
        if (this.direction.isEmpty()) {
            missing.add("наименование направления");
        }
        if (this.studyForm.isEmpty()) {
            missing.add("форма обучения");
        }
        if (!YEAR.matcher(this.year).matches()) {
            missing.add("год набора, четыре цифры");
        }
        return missing;
    }

    /** The key of the profile within a direction: case and punctuation aside. */
    public String profileKey() {
        return PlanRow.key(this.profile);
    }

    public String code() {
        return this.code;
    }

    public String direction() {
        return this.direction;
    }

    public String profile() {
        return this.profile;
    }

    public String qualification() {
        return this.qualification;
    }

    /** «очная», «заочная», «очно-заочная». */
    public String studyForm() {
        return this.studyForm;
    }

    public String studyTerm() {
        return this.studyTerm;
    }

    public String year() {
        return this.year;
    }

    /** The admission year; call after {@link #missing()} came back empty. */
    public int admissionYear() {
        return Integer.parseInt(this.year);
    }

    private static String clean(final String value) {
        return value == null ? "" : Cells.collapse(value);
    }
}
