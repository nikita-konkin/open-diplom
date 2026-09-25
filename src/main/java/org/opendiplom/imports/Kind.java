package org.opendiplom.imports;

/** Kind of work a statement subject becomes in the supplement. */
public enum Kind {
    DISCIPLINE("дисциплина"),
    PRACTICE("практика"),
    COURSE_WORK("курсовая"),
    ELECTIVE("факультатив"),
    /** The subject has no grade yet, so its kind is not known. */
    UNKNOWN("");

    private final String title;

    Kind(final String title) {
        this.title = title;
    }

    /** Word used in labels «Название_дисциплина_3». */
    public String title() {
        return this.title;
    }
}
