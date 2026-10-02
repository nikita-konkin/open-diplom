package org.opendiplom.catalog;

/** A graded element of a graduate: what the supplement prints for it. */
public final class ResultRecord {
    public static final String DISCIPLINE = "дисциплина";
    public static final String PRACTICE = "практика";
    public static final String FACULTATIVE = "факультатив";
    public static final String COURSE_WORK = "курсовая";

    /** Position of the plan element in the edition of the graduation. */
    public final int element;
    /** {@link #DISCIPLINE}, {@link #PRACTICE}, {@link #FACULTATIVE} or {@link #COURSE_WORK}. */
    public final String kind;
    /** The name for the supplement: the chosen elective, «Вид (тип)» of a practice. */
    public final String printed;
    /** Grade code 2–7, {@code null} when the statement has none or not a code. */
    public final Integer grade;
    /** The grade as the statement has it, empty when there is none. */
    public final String gradeText;
    /** Credits from the plan, {@code null} for a course work. */
    public final Double credits;

    public ResultRecord(
        final int element, final String kind, final String printed, final Integer grade, final String gradeText,
        final Double credits
    ) {
        this.element = element;
        this.kind = kind;
        this.printed = printed;
        this.grade = grade;
        this.gradeText = gradeText == null ? "" : gradeText;
        this.credits = credits;
    }
}
