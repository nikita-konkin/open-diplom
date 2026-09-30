package org.opendiplom.plans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What changed from one plan to another: elements added and removed, their
 * credits and forms of control, and the totals a supplement prints.
 *
 * <p>Elements are matched by the key of their printed name, so a renumbered
 * element is the same element; a name met twice is matched in order. Parts
 * and blocks are not compared: they are the sums of their elements.
 */
public final class PlanDiff {
    /** What happened to an element or the totals. */
    public enum Kind {
        ADDED, REMOVED, CHANGED, TOTALS
    }

    /** A change of one element, or of the totals. */
    public static final class Change {
        private final Kind kind;
        private final String name;
        private final List<String> details;

        Change(final Kind kind, final String name, final List<String> details) {
            this.kind = kind;
            this.name = name;
            this.details = Collections.unmodifiableList(details);
        }

        public Kind kind() {
            return this.kind;
        }

        /** The printed name of the element; empty for the totals. */
        public String name() {
            return this.name;
        }

        /** «з.е.: 6 → 9», «зачеты с оценкой: 6 → 4,6». */
        public List<String> details() {
            return this.details;
        }

        @Override
        public String toString() {
            return this.kind + " " + this.name + " " + this.details;
        }
    }

    private final List<Change> changes;

    private PlanDiff(final List<Change> changes) {
        this.changes = Collections.unmodifiableList(changes);
    }

    public static PlanDiff of(final PlanStructure before, final PlanStructure after) {
        final Map<String, PlanItem> earlier = keyed(before);
        final List<Change> changes = new ArrayList<>();
        for (final Map.Entry<String, PlanItem> entry : keyed(after).entrySet()) {
            final PlanItem now = entry.getValue();
            final PlanItem then = earlier.remove(entry.getKey());
            if (then == null) {
                changes.add(new Change(Kind.ADDED, now.printed(), describe(now)));
                continue;
            }
            final List<String> details = new ArrayList<>();
            compare("з.е.", number(then.row().credits()), number(now.row().credits()), details);
            for (int form = 0; form < PlanRow.CONTROLS; ++form) {
                compare(
                    PlanRow.CONTROL_NAMES.get(form), then.row().controls().get(form), now.row().controls().get(form),
                    details
                );
            }
            if (!details.isEmpty()) {
                changes.add(new Change(Kind.CHANGED, now.printed(), details));
            }
        }
        for (final PlanItem gone : earlier.values()) {
            changes.add(new Change(Kind.REMOVED, gone.printed(), describe(gone)));
        }
        final PlanTotals then = PlanTotals.of(rows(before));
        final PlanTotals now = PlanTotals.of(rows(after));
        final List<String> totals = new ArrayList<>();
        compare("объём программы, з.е.", number(then.program()), number(now.program()), totals);
        compare("практики, з.е.", number(then.practices()), number(now.practices()), totals);
        compare("ГИА, з.е.", number(then.attestation()), number(now.attestation()), totals);
        compare("контактная работа, ч", number(then.contact()), number(now.contact()), totals);
        if (!totals.isEmpty()) {
            changes.add(new Change(Kind.TOTALS, "", totals));
        }
        return new PlanDiff(changes);
    }

    public List<Change> changes() {
        return this.changes;
    }

    /** Whether the plans are the same in everything compared. */
    public boolean same() {
        return this.changes.isEmpty();
    }

    /** Leaves by the key of the printed name; the second of a name gets «#2». */
    private static Map<String, PlanItem> keyed(final PlanStructure plan) {
        final Map<String, PlanItem> keyed = new LinkedHashMap<>();
        for (final PlanItem leaf : plan.leaves()) {
            final String key = PlanRow.key(leaf.printed());
            int occurrence = 1;
            while (keyed.containsKey(occurrence == 1 ? key : key + "#" + occurrence)) {
                ++occurrence;
            }
            keyed.put(occurrence == 1 ? key : key + "#" + occurrence, leaf);
        }
        return keyed;
    }

    private static List<PlanRow> rows(final PlanStructure plan) {
        final List<PlanRow> rows = new ArrayList<>();
        for (final PlanItem item : plan.items()) {
            rows.add(item.row());
        }
        return rows;
    }

    /** Credits and forms of control of an element added or removed. */
    private static List<String> describe(final PlanItem item) {
        final List<String> details = new ArrayList<>();
        if (item.row().credits() != null) {
            details.add("з.е.: " + number(item.row().credits()));
        }
        for (int form = 0; form < PlanRow.CONTROLS; ++form) {
            if (!item.row().controls().get(form).isEmpty()) {
                details.add(PlanRow.CONTROL_NAMES.get(form) + ": " + item.row().controls().get(form));
            }
        }
        return details;
    }

    private static void compare(final String what, final String before, final String after, final List<String> details) {
        if (!Objects.equals(before, after)) {
            details.add(what + ": " + (before.isEmpty() ? "—" : before) + " → " + (after.isEmpty() ? "—" : after));
        }
    }

    private static String number(final Double value) {
        return value == null ? "" : PlanTotals.number(value);
    }
}
