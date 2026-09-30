package org.opendiplom.plans;

import java.util.Collections;
import java.util.List;

/**
 * A row of a plan in its place: what it is, which block it belongs to, whose
 * part it is and how a supplement names it.
 *
 * <p>Indexes do not identify rows: a plan may give the same index to two rows
 * (the final qualifying work and the state exam, both «Б.3.1.1»), so a row is
 * known by its position.
 */
public final class PlanItem {
    /** What a row is. */
    public enum Kind {
        /** «Блок 1. Дисциплины (модули)». */
        BLOCK,
        /** «Обязательная часть», «Часть, формируемая участниками…». */
        PART,
        /** A discipline, a practice, a group of them, an attestation or a facultative. */
        ELEMENT,
        /** A row without an index and values: «Факультативные дисциплины». */
        HEADING,
        /** «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ». */
        TOTAL
    }

    /** Where a row stands: the block, or the facultatives outside the blocks. */
    public enum Section {
        DISCIPLINES, PRACTICES, ATTESTATION, FACULTATIVES, NONE
    }

    private final int position;
    private final PlanRow row;
    private final Kind kind;
    private final Section section;
    private final int parent;
    private final boolean leaf;
    private final String printed;
    private final List<String> alternatives;

    PlanItem(
        final int position, final PlanRow row, final Kind kind, final Section section, final int parent,
        final boolean leaf, final String printed, final List<String> alternatives
    ) {
        this.position = position;
        this.row = row;
        this.kind = kind;
        this.section = section;
        this.parent = parent;
        this.leaf = leaf;
        this.printed = printed;
        this.alternatives = Collections.unmodifiableList(alternatives);
    }

    /** Place in the plan from 0, in the order printed. */
    public int position() {
        return this.position;
    }

    public PlanRow row() {
        return this.row;
    }

    public Kind kind() {
        return this.kind;
    }

    public Section section() {
        return this.section;
    }

    /** Position of the row this one is a part of, or -1. */
    public int parent() {
        return this.parent;
    }

    /** Whether this is an element with nothing under it: what a graduate gets a grade for. */
    public boolean leaf() {
        return this.leaf;
    }

    /**
     * The name for the supplement: a practice as «Вид (тип)», «Производственная
     * практика (преддипломная практика)», everything else as in the plan.
     */
    public String printed() {
        return this.printed;
    }

    /** Disciplines to choose from, «A / B» in the brackets of an elective; empty for others. */
    public List<String> alternatives() {
        return this.alternatives;
    }

    /** «Б.1.1.1 «Математика»» or «Блок 1…» for messages. */
    public String label() {
        return this.row.index().isEmpty()
            ? "«" + this.row.name() + "»"
            : this.row.index() + " «" + this.row.name() + "»";
    }
}
