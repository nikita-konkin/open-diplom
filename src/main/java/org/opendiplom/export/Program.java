package org.opendiplom.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/**
 * The educational program of a group: what the XML writes for every student
 * besides personal data and grades.
 *
 * <p>The direction (code and name) and the profile are different fields
 * (D-02): the 2026 supplements printed the profile in place of the direction.
 */
public final class Program {
    private static final Pattern DIRECTION = Pattern.compile("^(\\d{2}\\.(\\d{2})\\.\\d{2})\\s+(\\S.*)$");
    private static final Map<String, String> QUALIFICATION_BY_LEVEL = new HashMap<>();

    static {
        // specialist qualifications have program-specific names and are not checked
        QUALIFICATION_BY_LEVEL.put("03", "бакалавр");
        QUALIFICATION_BY_LEVEL.put("04", "магистр");
    }

    final String directionCode;
    final String directionName;
    final String profile;
    final String qualification;
    final String studyForm;
    final String studyTerm;
    final Object programCredits;
    final Object contactHours;
    final Object practiceCredits;
    final Object finalCredits;
    final String gekChairman;

    private Program(final Builder builder, final Matcher direction, final String profile) {
        this.directionCode = direction.group(1);
        this.directionName = direction.group(3);
        this.profile = profile;
        this.qualification = builder.qualification;
        this.studyForm = builder.studyForm;
        this.studyTerm = builder.studyTerm;
        this.programCredits = builder.programCredits;
        this.contactHours = builder.contactHours;
        this.practiceCredits = builder.practiceCredits;
        this.finalCredits = builder.finalCredits;
        this.gekChairman = builder.gekChairman;
    }

    public String directionCode() {
        return this.directionCode;
    }

    /** Settings of a program, checked by {@link #build()}. */
    public static final class Builder {
        private String direction;
        private String profile;
        private String qualification;
        private String studyForm;
        private String studyTerm;
        private Object programCredits;
        private Object contactHours;
        private Object practiceCredits;
        private Object finalCredits;
        private String gekChairman;

        /** Code and name from the curriculum header: «09.03.02 ИНФОРМАЦИОННЫЕ СИСТЕМЫ И ТЕХНОЛОГИИ». */
        public Builder direction(final String value) {
            this.direction = value;
            return this;
        }

        /** Profile (направленность) without the code. */
        public Builder profile(final String value) {
            this.profile = value;
            return this;
        }

        public Builder qualification(final String value) {
            this.qualification = value;
            return this;
        }

        public Builder studyForm(final String value) {
            this.studyForm = value;
            return this;
        }

        /** «4 года». */
        public Builder studyTerm(final String value) {
            this.studyTerm = value;
            return this;
        }

        /** Program volume, credits. */
        public Builder programCredits(final Object value) {
            this.programCredits = value;
            return this;
        }

        /** Contact hours as printed: «3180 ак.час». */
        public Builder contactHours(final Object value) {
            this.contactHours = value;
            return this;
        }

        /** Credits of all practices together. */
        public Builder practiceCredits(final Object value) {
            this.practiceCredits = value;
            return this;
        }

        /** Credits of the state final attestation (ГИА). */
        public Builder finalCredits(final Object value) {
            this.finalCredits = value;
            return this;
        }

        public Builder gekChairman(final String value) {
            this.gekChairman = value;
            return this;
        }

        /**
         * @throws ValidationProblems the direction, profile and qualification disagree
         */
        public Program build() throws ValidationProblems {
            final List<String> problems = new ArrayList<>();
            final String code = Cells.collapse(this.direction == null ? "" : this.direction);
            final String named = Cells.collapse(this.profile == null ? "" : this.profile);
            final Matcher match = DIRECTION.matcher(code);
            if (!match.matches()) {
                problems.add(
                    "Направление подготовки: нужен код вида 09.03.02 и через пробел "
                        + "наименование из шапки учебного плана, получено «" + code + "»"
                );
            } else {
                final String expected = QUALIFICATION_BY_LEVEL.get(match.group(2));
                final String qualification = String.valueOf(this.qualification).strip()
                    .toLowerCase(Locale.ROOT);
                if (expected != null && !qualification.equals(expected)) {
                    problems.add(
                        "Направление " + match.group(1) + " относится к уровню «" + expected
                            + "», а выбрана квалификация «" + qualification + "»"
                    );
                }
                if (Names.key(named).equals(Names.key(match.group(3)))) {
                    problems.add(
                        "Профиль совпадает с наименованием направления подготовки. "
                            + "В поле направления укажите наименование из шапки учебного "
                            + "плана (например, «09.03.02 ИНФОРМАЦИОННЫЕ СИСТЕМЫ И "
                            + "ТЕХНОЛОГИИ»), а профиль — в отдельном поле"
                    );
                }
            }
            if (named.isEmpty()) {
                problems.add("Профиль (направленность) образовательной программы не указан");
            } else if (DIRECTION.matcher(named).matches()) {
                problems.add(
                    "Профиль «" + named + "» указан с кодом направления: "
                        + "в поле профиля нужно только его наименование"
                );
            }
            if (!problems.isEmpty()) {
                throw new ValidationProblems(problems);
            }
            return new Program(this, match, named);
        }
    }
}
