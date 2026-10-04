package org.opendiplom.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «С отличием» by item 27 of Order No 670: every grade of the supplement is
 * «отлично» or «хорошо», every grade of the state final attestation is
 * «отлично», and «отлично» are at least 75 % of the grades with the
 * attestation; «зачтено» does not count.
 *
 * <p>The university counts every grade of the supplement, the facultative
 * disciplines too (the owner, 02.10.2026), as the item allows. The rule only
 * proposes: the operator decides (ADR-0010).
 */
public final class Honors {
    private static final int EXCELLENT = 5;
    private static final int GOOD = 4;
    private static final int PASSED = 6;
    private static final int PLACEHOLDER = 7;

    /** {@code null} when the grades do not allow to say yet. */
    public final Boolean proposal;
    /** «отлично» among the grades counted, with the attestation. */
    public final int excellent;
    /** Grades counted: 2–5, without «зачтено». */
    public final int counted;
    /** The mean of the grades counted, {@code null} without any. */
    public final Double average;
    /** How it was reckoned, for the operator. */
    public final String explanation;

    private Honors(
        final Boolean proposal, final int excellent, final int counted, final Double average, final String explanation
    ) {
        this.proposal = proposal;
        this.excellent = excellent;
        this.counted = counted;
        this.average = average;
        this.explanation = explanation;
    }

    /**
     * @param stateExam whether the plan has a state exam besides the thesis
     */
    public static Honors of(final GraduateRecord graduate, final boolean stateExam) {
        final List<Integer> grades = new ArrayList<>();
        final List<String> unknown = new ArrayList<>();
        for (final ResultRecord result : graduate.results) {
            if (result.grade == null) {
                // a facultative without a grade is not printed; any other result lacks one
                if (!ResultRecord.FACULTATIVE.equals(result.kind) || !result.gradeText.isEmpty()) {
                    unknown.add("«" + result.printed + "»");
                }
            } else if (result.grade == PLACEHOLDER) {
                unknown.add("«" + result.printed + "» (код 7)");
            } else if (result.grade != PASSED) {
                grades.add(result.grade);
            }
        }
        final List<Integer> attestation = new ArrayList<>();
        attestation(graduate.thesisGrade, "ВКР", attestation, unknown);
        if (stateExam) {
            attestation(graduate.stateExamGrade, "государственный экзамен", attestation, unknown);
        }
        grades.addAll(attestation);
        int excellent = 0;
        int sum = 0;
        int lowest = EXCELLENT;
        for (final int grade : grades) {
            excellent += grade == EXCELLENT ? 1 : 0;
            sum += grade;
            lowest = Math.min(lowest, grade);
        }
        final Double average = grades.isEmpty() ? null : (double) sum / grades.size();
        final StringBuilder explanation = new StringBuilder("«отлично» ").append(excellent).append(" из ")
            .append(grades.size());
        if (!grades.isEmpty()) {
            explanation.append(" (").append(Math.round(100.0 * excellent / grades.size())).append(" %)")
                .append(", средний балл ").append(String.format(Locale.ROOT, "%.2f", average).replace('.', ','));
        }
        if (!unknown.isEmpty()) {
            explanation.append("; нельзя рассчитать, пока нет оценки: ").append(String.join(", ", unknown));
            return new Honors(null, excellent, grades.size(), average, explanation.toString());
        }
        final boolean good = lowest >= GOOD;
        final boolean attested = attestation.stream().allMatch(grade -> grade == EXCELLENT);
        final boolean share = !grades.isEmpty() && 4 * excellent >= 3 * grades.size();
        explanation.append(good ? "; ниже «хорошо» нет" : "; есть оценки ниже «хорошо»")
            .append(attested ? "; ГИА «отлично»" : "; ГИА не только «отлично»")
            .append(share ? "" : "; «отлично» меньше 75 %");
        return new Honors(good && attested && share, excellent, grades.size(), average, explanation.toString());
    }

    private static void attestation(
        final Integer grade, final String what, final List<Integer> grades, final List<String> unknown
    ) {
        if (grade == null || grade == PLACEHOLDER) {
            unknown.add(what + (grade == null ? "" : " (код 7)"));
        } else if (grade != PASSED) {
            grades.add(grade);
        }
    }
}
