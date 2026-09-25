package org.opendiplom.imports;

/**
 * One subject and kind of work of one student, all semesters together.
 */
public final class StudyRecord {
    private final String name;
    private final Kind kind;
    private final double hours;
    private final Object grade;
    private final int ungraded;

    public StudyRecord(
        final String name, final Kind kind, final double hours,
        final Object grade, final int ungraded
    ) {
        this.name = name;
        this.kind = kind;
        this.hours = hours;
        this.grade = grade;
        this.ungraded = ungraded;
    }

    public String name() {
        return this.name;
    }

    public Kind kind() {
        return this.kind;
    }

    /** Hours of all semesters, course work included for a discipline. */
    public double hours() {
        return this.hours;
    }

    /** Grade as written in the statement, {@code null} when there is none. */
    public Object grade() {
        return this.grade;
    }

    /** Semester rows of the subject without any grade. */
    public int ungraded() {
        return this.ungraded;
    }

    /** Credits counted from the hours: 36 hours make one credit. */
    public int credits() {
        return Credits.ofHours(this.hours);
    }

    /** Label «Название_дисциплина_3»; a subject without a grade has its name only. */
    public String label() {
        if (this.kind == Kind.UNKNOWN) {
            return this.name;
        }
        return this.name + '_' + this.kind.title() + '_' + this.credits();
    }
}
