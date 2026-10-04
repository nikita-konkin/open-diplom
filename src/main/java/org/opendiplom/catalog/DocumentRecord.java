package org.opendiplom.catalog;

/**
 * A diploma with its supplement, or a duplicate of them: the registration
 * number and the date of issue they share (Order No 670) and «с отличием».
 */
public final class DocumentRecord {
    /** {@code null} for a document not yet saved. */
    public final String id;
    public final String graduateId;
    /** The document a duplicate replaces, {@code null} for the original. */
    public final String duplicateOf;
    public final boolean diplomaDuplicate;
    public final boolean supplementDuplicate;
    /** Empty until given. */
    public final String regNumber;
    /** ГГГГ-ММ-ДД, empty until set. */
    public final String issueDate;
    /** The operator's decision on «с отличием», {@code null} to follow the rule. */
    public final Boolean honors;

    public DocumentRecord(
        final String id, final String graduateId, final String duplicateOf, final boolean diplomaDuplicate,
        final boolean supplementDuplicate, final String regNumber, final String issueDate, final Boolean honors
    ) {
        this.id = id;
        this.graduateId = graduateId;
        this.duplicateOf = duplicateOf;
        this.diplomaDuplicate = diplomaDuplicate;
        this.supplementDuplicate = supplementDuplicate;
        this.regNumber = regNumber == null ? "" : regNumber;
        this.issueDate = issueDate == null ? "" : issueDate;
        this.honors = honors;
    }

    /** The original document of a graduate who has none yet. */
    public static DocumentRecord blank(final String graduateId) {
        return new DocumentRecord(null, graduateId, null, false, false, "", "", null);
    }

    public boolean duplicate() {
        return this.duplicateOf != null;
    }

    /** «с отличием» as printed: the operator's decision, else the rule. */
    public boolean honors(final Honors rule) {
        return this.honors != null ? this.honors : Boolean.TRUE.equals(rule.proposal);
    }

    /** «дубликат диплома и приложения», «дубликат приложения»; empty for the original. */
    public String duplicateTitle() {
        if (!this.duplicate()) {
            return "";
        }
        if (this.diplomaDuplicate && this.supplementDuplicate) {
            return "дубликат диплома и приложения";
        }
        return this.diplomaDuplicate ? "дубликат диплома" : "дубликат приложения";
    }
}
