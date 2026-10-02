package org.opendiplom.graduation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opendiplom.export.Names;
import org.opendiplom.export.StudentInfo;

/**
 * Students of the information file and sheets of the statement, matched by
 * «Фамилия И. О.» (ADR-0004). What cannot be matched one to one is left for
 * the operator: a graduate is never dropped or given another student's
 * grades silently.
 */
public final class StudentMatch {
    /** Where a choice about a row of the information file is kept: «info:5». */
    public static final String INFO = "info:";
    /** Where a choice about a sheet nobody claims is kept: «sheet:Иванов И. И.». */
    public static final String SHEET = "sheet:";
    /** The choice «leave out of the graduation». */
    public static final String EXCLUDED = "-";

    /** The four groups of the report, and the rows the operator left out. */
    public enum Status {
        MATCHED("совпало"),
        CHOSEN("связано вручную"),
        NO_STATEMENT("нет в ведомости"),
        NO_INFO("нет в сведениях"),
        AMBIGUOUS("неоднозначно"),
        EXCLUDED("не включён в выпуск");

        private final String title;

        Status(final String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }

        /** Whether the operator still has to act. */
        public boolean open() {
            return this == NO_STATEMENT || this == NO_INFO || this == AMBIGUOUS;
        }
    }

    /** A row of the information file with its sheet, or a sheet nobody claims. */
    public static final class Pair {
        public final StudentInfo.Entry entry;
        public final String student;
        public final Status status;
        /** Sheets that fit the row, for the operator to choose from. */
        public final List<String> candidates;

        Pair(final StudentInfo.Entry entry, final String student, final Status status, final List<String> candidates) {
            this.entry = entry;
            this.student = student;
            this.status = status;
            this.candidates = Collections.unmodifiableList(candidates);
        }

        /** Key of the choice about this pair. */
        public String item() {
            return this.entry == null ? SHEET + this.student : INFO + this.entry.line;
        }
    }

    private final List<Pair> pairs;

    private StudentMatch(final List<Pair> pairs) {
        this.pairs = Collections.unmodifiableList(pairs);
    }

    /**
     * @param students names of the statement sheets, «Фамилия И. О.»
     * @param choices what the operator decided, by {@link Pair#item()}
     */
    public static StudentMatch of(
        final List<StudentInfo.Entry> entries, final List<String> students, final Map<String, String> choices
    ) {
        final Map<StudentInfo.Entry, List<String>> candidates = new LinkedHashMap<>();
        final Map<StudentInfo.Entry, String> taken = new LinkedHashMap<>();
        for (final StudentInfo.Entry entry : entries) {
            final List<String> exact = new ArrayList<>();
            final List<String> near = new ArrayList<>();
            for (final String student : students) {
                final List<String> key = Names.column(student);
                if (key.equals(entry.key())) {
                    exact.add(student);
                }
                if (Names.compatible(key, entry.key())) {
                    near.add(student);
                }
            }
            final List<String> found = exact.isEmpty() ? near : exact;
            candidates.put(entry, found);
            final String choice = choices.get(INFO + entry.line);
            if (choice != null && (EXCLUDED.equals(choice) || students.contains(choice))) {
                taken.put(entry, choice);
            } else if (found.size() == 1) {
                taken.put(entry, found.get(0));
            }
        }
        final Map<String, Integer> claims = new LinkedHashMap<>();
        for (final String student : taken.values()) {
            claims.merge(student, 1, Integer::sum);
        }
        final List<Pair> pairs = new ArrayList<>();
        for (final StudentInfo.Entry entry : entries) {
            final String student = taken.get(entry);
            final List<String> found = candidates.get(entry);
            final boolean chosen = choices.containsKey(INFO + entry.line);
            if (EXCLUDED.equals(student)) {
                pairs.add(new Pair(entry, null, Status.EXCLUDED, found));
            } else if (student != null && claims.get(student) == 1) {
                pairs.add(new Pair(entry, student, chosen ? Status.CHOSEN : Status.MATCHED, found));
            } else if (student != null || found.size() > 1) {
                // a sheet two rows claim belongs to neither until the operator decides
                final List<String> options = new ArrayList<>(found);
                if (student != null && !options.contains(student)) {
                    options.add(student);
                }
                pairs.add(new Pair(entry, null, Status.AMBIGUOUS, options));
            } else {
                pairs.add(new Pair(entry, null, Status.NO_STATEMENT, found));
            }
        }
        for (final String student : students) {
            if (!claims.containsKey(student)) {
                final Status status = EXCLUDED.equals(choices.get(SHEET + student)) ? Status.EXCLUDED : Status.NO_INFO;
                pairs.add(new Pair(null, student, status, Collections.emptyList()));
            }
        }
        return new StudentMatch(pairs);
    }

    /** Rows of the information file in its order, then the sheets nobody claims. */
    public List<Pair> pairs() {
        return this.pairs;
    }

    /** Whether nothing is left for the operator. */
    public boolean resolved() {
        return this.pairs.stream().noneMatch(pair -> pair.status.open());
    }

    /** Pairs of a status. */
    public List<Pair> with(final Status status) {
        final List<Pair> found = new ArrayList<>();
        for (final Pair pair : this.pairs) {
            if (pair.status == status) {
                found.add(pair);
            }
        }
        return found;
    }

    /** The graduates: rows of the file with their sheets, in the order of the file. */
    public List<Pair> graduates() {
        final List<Pair> graduates = new ArrayList<>();
        for (final Pair pair : this.pairs) {
            if (pair.entry != null && pair.student != null) {
                graduates.add(pair);
            }
        }
        return graduates;
    }
}
