package org.opendiplom.graduation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.opendiplom.imports.Kind;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.imports.StudyRecord;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;

/**
 * Subjects of a statement matched to the elements of a plan, in this order:
 * the link the operator confirmed before for the program, the same name,
 * the only element whose name contains it or is contained in it, a proposal
 * the operator confirms, and the operator's choice from the list.
 *
 * <p>An element may be a leaf or a group: a part-time statement grades the
 * two parts of the technological practice together, as the group of 9
 * credits. Of equally named elements, the one with the credits of the
 * statement hours is taken.
 */
public final class SubjectMatch {
    /** Where the operator's choice about a subject is kept: «subject:дисциплина:математика». */
    public static final String PREFIX = "subject:";
    private static final int NEAR_KEY = 10;
    private static final double SIMILAR = 0.5;
    /** «(Элективная дисциплина 1)» that «Деканат» adds after the discipline chosen. */
    private static final Pattern ELECTIVE_NOTE = Pattern.compile(
        "\\s*\\((?:[^()]*(?:электив|по выбору)[^()]*)\\)\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** How a subject found its element. */
    public enum Status {
        REMEMBERED("запомненная связь", true),
        EXACT("совпадает название", true),
        CONTAINED("однозначное вхождение", true),
        PROPOSED("предложено, подтвердите", false),
        CHOSEN("выбрано вручную", false),
        EXCLUDED("не включать в приложение", false),
        NONE("нет в плане, выберите", false),
        CONFLICT("у студента два предмета на одном элементе", false);

        private final String title;
        private final boolean automatic;

        Status(final String title, final boolean automatic) {
            this.title = title;
            this.automatic = automatic;
        }

        public String title() {
            return this.title;
        }

        /** Found without the operator. */
        public boolean automatic() {
            return this.automatic;
        }

        /** Whether the operator still has to act. */
        public boolean open() {
            return this == PROPOSED || this == NONE || this == CONFLICT;
        }
    }

    /** A subject of the statement and kind of work, all students together. */
    public static final class Subject {
        public final String name;
        public final Kind kind;
        /** Credits by the statement hours, as most students have them. */
        public final int counted;
        public final int students;
        /** Students with a semester of the subject that has no grade. */
        public final int ungraded;

        Subject(final String name, final Kind kind, final int counted, final int students, final int ungraded) {
            this.name = name;
            this.kind = kind;
            this.counted = counted;
            this.students = students;
            this.ungraded = ungraded;
        }

        /** «дисциплина:математика»: the kind and the name key. */
        public String key() {
            return key(this.name, this.kind);
        }

        static String key(final String name, final Kind kind) {
            return kind.title() + ':' + PlanRow.key(name);
        }
    }

    /** A plan element, and the discipline chosen when the element is an elective. */
    public static final class Target {
        /** The subject does not go to the supplement. */
        public static final Target NOWHERE = new Target(-1, "");

        public final int position;
        public final String alternative;

        public Target(final int position, final String alternative) {
            this.position = position;
            this.alternative = alternative == null ? "" : alternative;
        }

        /** «12» or «12|Название», «-» for {@link #NOWHERE}. */
        public String code() {
            if (this.position < 0) {
                return StudentMatch.EXCLUDED;
            }
            return this.alternative.isEmpty() ? String.valueOf(this.position) : this.position + "|" + this.alternative;
        }

        /** The target of a code, {@code null} when the code is not one. */
        public static Target parse(final String code) {
            if (code == null || code.isEmpty()) {
                return null;
            }
            if (StudentMatch.EXCLUDED.equals(code)) {
                return NOWHERE;
            }
            final String[] parts = code.split("\\|", 2);
            try {
                return new Target(Integer.parseInt(parts[0]), parts.length > 1 ? parts[1] : "");
            } catch (final NumberFormatException error) {
                return null;
            }
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Target && ((Target) other).code().equals(this.code());
        }

        @Override
        public int hashCode() {
            return this.code().hashCode();
        }
    }

    /** A link confirmed before, as the registry keeps it for the program. */
    public static final class Link {
        public final String subjectKey;
        /** Empty: the subject does not go to the supplement. */
        public final String elementIndex;
        public final String elementKey;
        public final String alternativeKey;

        public Link(final String subjectKey, final String elementIndex, final String elementKey, final String alternativeKey) {
            this.subjectKey = subjectKey;
            this.elementIndex = elementIndex;
            this.elementKey = elementKey;
            this.alternativeKey = alternativeKey;
        }

        /** The link that keeps a target of a plan beyond its edition. */
        public static Link of(final String subjectKey, final PlanStructure plan, final Target target) {
            if (target.position < 0) {
                return new Link(subjectKey, "", "", "");
            }
            final PlanRow row = plan.items().get(target.position).row();
            return new Link(subjectKey, row.index(), PlanRow.key(row.name()), PlanRow.key(target.alternative));
        }
    }

    /** A subject with its element and what the operator should check. */
    public static final class Row {
        public final Subject subject;
        public final Status status;
        /** {@code null} while there is none. */
        public final Target target;
        public final List<String> notes;

        Row(final Subject subject, final Status status, final Target target, final List<String> notes) {
            this.subject = subject;
            this.status = status;
            this.target = target;
            this.notes = Collections.unmodifiableList(notes);
        }
    }

    private final PlanStructure plan;
    private final List<Row> rows;

    private SubjectMatch(final PlanStructure plan, final List<Row> rows) {
        this.plan = plan;
        this.rows = Collections.unmodifiableList(rows);
    }

    /**
     * @param students the students whose subjects count
     * @param links links confirmed before, by subject key
     * @param choices what the operator decided, by {@link #PREFIX} and subject key
     */
    public static SubjectMatch of(
        final StatementImport statement, final List<String> students, final PlanStructure plan,
        final Map<String, Link> links, final Map<String, String> choices
    ) {
        final List<Subject> subjects = subjects(statement, students);
        final Map<String, Status> statuses = new LinkedHashMap<>();
        final Map<String, Target> targets = new LinkedHashMap<>();
        for (final Subject subject : subjects) {
            final Target chosen = Target.parse(choices.get(PREFIX + subject.key()));
            if (chosen != null && valid(plan, subject, chosen)) {
                statuses.put(subject.key(), chosen.position < 0 ? Status.EXCLUDED : Status.CHOSEN);
                targets.put(subject.key(), chosen);
                continue;
            }
            final Target remembered = remembered(plan, links.get(subject.key()));
            if (remembered != null && valid(plan, subject, remembered)) {
                statuses.put(subject.key(), Status.REMEMBERED);
                targets.put(subject.key(), remembered);
                continue;
            }
            find(plan, subject, statuses, targets);
        }
        final Set<String> conflicts = conflicts(statement, students, targets);
        final List<Row> rows = new ArrayList<>();
        for (final Subject subject : subjects) {
            final Status status = conflicts.contains(subject.key()) ? Status.CONFLICT : statuses.get(subject.key());
            final Target target = targets.get(subject.key());
            rows.add(new Row(subject, status, target, notes(plan, subject, target)));
        }
        return new SubjectMatch(plan, rows);
    }

    public List<Row> rows() {
        return this.rows;
    }

    /** Whether nothing is left for the operator. */
    public boolean resolved() {
        return this.rows.stream().noneMatch(row -> row.status.open());
    }

    /** Subjects matched without the operator. */
    public long automatic() {
        return this.rows.stream().filter(row -> row.status.automatic()).count();
    }

    /**
     * The element of a subject of a student's record, {@code null} when there
     * is none yet. A subject the student has no grade for has no kind; it
     * goes where the same subject of the others goes.
     */
    public Target target(final StudyRecord record) {
        for (final Row row : this.rows) {
            final boolean same = record.kind() == Kind.UNKNOWN
                ? row.subject.kind != Kind.COURSE_WORK && PlanRow.key(row.subject.name).equals(PlanRow.key(record.name()))
                : row.subject.key().equals(Subject.key(record.name(), record.kind()));
            if (same) {
                return row.target;
            }
        }
        return null;
    }

    /** Elements a subject of a kind may go to, for the operator's list. */
    public List<Target> options(final Kind kind) {
        final List<Target> options = new ArrayList<>();
        for (final PlanItem item : this.plan.items()) {
            if (fits(item, kind)) {
                if (item.alternatives().isEmpty()) {
                    options.add(new Target(item.position(), ""));
                }
                for (final String alternative : item.alternatives()) {
                    options.add(new Target(item.position(), alternative));
                }
            }
        }
        return options;
    }

    public PlanStructure plan() {
        return this.plan;
    }

    /** Subjects of the students in the order they first appear; ungraded ones are left to the checks. */
    private static List<Subject> subjects(final StatementImport statement, final List<String> students) {
        final Map<String, String> names = new LinkedHashMap<>();
        final Map<String, Kind> kinds = new LinkedHashMap<>();
        final Map<String, Map<Integer, Integer>> counted = new LinkedHashMap<>();
        final Map<String, Integer> taking = new LinkedHashMap<>();
        final Map<String, Integer> ungraded = new LinkedHashMap<>();
        for (final String student : students) {
            for (final StudyRecord record : statement.records(student)) {
                if (record.kind() == Kind.UNKNOWN) {
                    continue;
                }
                final String key = Subject.key(record.name(), record.kind());
                names.putIfAbsent(key, record.name());
                kinds.putIfAbsent(key, record.kind());
                counted.computeIfAbsent(key, k -> new LinkedHashMap<>()).merge(record.credits(), 1, Integer::sum);
                taking.merge(key, 1, Integer::sum);
                if (record.ungraded() > 0) {
                    ungraded.merge(key, 1, Integer::sum);
                }
            }
        }
        final List<Subject> subjects = new ArrayList<>();
        for (final Map.Entry<String, String> entry : names.entrySet()) {
            int credits = 0;
            int most = 0;
            for (final Map.Entry<Integer, Integer> count : counted.get(entry.getKey()).entrySet()) {
                if (count.getValue() > most) {
                    most = count.getValue();
                    credits = count.getKey();
                }
            }
            subjects.add(new Subject(
                entry.getValue(), kinds.get(entry.getKey()), credits, taking.get(entry.getKey()),
                ungraded.getOrDefault(entry.getKey(), 0)
            ));
        }
        return subjects;
    }

    /** The element of a link in this plan: by name, and by index among equally named ones. */
    private static Target remembered(final PlanStructure plan, final Link link) {
        if (link == null) {
            return null;
        }
        if (link.elementKey.isEmpty()) {
            return Target.NOWHERE;
        }
        final List<PlanItem> named = new ArrayList<>();
        for (final PlanItem item : plan.items()) {
            if (item.kind() == PlanItem.Kind.ELEMENT && PlanRow.key(item.row().name()).equals(link.elementKey)) {
                named.add(item);
            }
        }
        if (named.size() > 1) {
            named.removeIf(item -> !item.row().index().equals(link.elementIndex));
        }
        if (named.size() != 1) {
            return null;
        }
        final PlanItem item = named.get(0);
        if (link.alternativeKey.isEmpty()) {
            return item.alternatives().isEmpty() ? new Target(item.position(), "") : null;
        }
        for (final String alternative : item.alternatives()) {
            if (PlanRow.key(alternative).equals(link.alternativeKey)) {
                return new Target(item.position(), alternative);
            }
        }
        return null;
    }

    private static void find(
        final PlanStructure plan, final Subject subject, final Map<String, Status> statuses,
        final Map<String, Target> targets
    ) {
        final List<String> variants = variants(subject.name);
        final Map<Target, List<String>> names = names(plan, subject.kind);
        final List<Target> exact = new ArrayList<>();
        final List<Target> contained = new ArrayList<>();
        Target similar = null;
        double best = 0;
        for (final Map.Entry<Target, List<String>> entry : names.entrySet()) {
            boolean equal = false;
            boolean near = false;
            for (final String name : entry.getValue()) {
                for (final String variant : variants) {
                    equal |= name.equals(variant);
                    near |= variant.length() >= NEAR_KEY && name.contains(variant)
                        || name.length() >= NEAR_KEY && variant.contains(name);
                    final double score = similarity(name, variant);
                    // half the words in common is enough to propose: the operator confirms
                    if (score >= SIMILAR && score > best) {
                        best = score;
                        similar = entry.getKey();
                    }
                }
            }
            if (equal) {
                exact.add(entry.getKey());
            } else if (near) {
                contained.add(entry.getKey());
            }
        }
        final List<Target> sameExact = narrowed(plan, subject, exact);
        final List<Target> sameContained = narrowed(plan, subject, contained);
        if (sameExact.size() == 1) {
            put(subject, Status.EXACT, sameExact.get(0), statuses, targets);
        } else if (!sameExact.isEmpty()) {
            put(subject, Status.PROPOSED, sameExact.get(0), statuses, targets);
        } else if (contained.size() == 1) {
            put(subject, Status.CONTAINED, contained.get(0), statuses, targets);
        } else if (!sameContained.isEmpty()) {
            put(subject, Status.PROPOSED, sameContained.get(0), statuses, targets);
        } else if (similar != null) {
            put(subject, Status.PROPOSED, similar, statuses, targets);
        } else {
            statuses.put(subject.key(), Status.NONE);
        }
    }

    private static void put(
        final Subject subject, final Status status, final Target target, final Map<String, Status> statuses,
        final Map<String, Target> targets
    ) {
        statuses.put(subject.key(), status);
        targets.put(subject.key(), target);
    }

    /**
     * Of several elements, those that fit best: with the credits of the
     * statement hours, and for a course work those that have one; of a group
     * and a row of it named alike, the row.
     */
    private static List<Target> narrowed(final PlanStructure plan, final Subject subject, final List<Target> targets) {
        if (targets.size() < 2) {
            return targets;
        }
        final List<Target> fitting = new ArrayList<>();
        for (final Target target : targets) {
            final PlanRow row = plan.items().get(target.position).row();
            final boolean fits = subject.kind == Kind.COURSE_WORK
                ? courseWork(row)
                : row.credits() != null && row.credits() == subject.counted;
            if (fits) {
                fitting.add(target);
            }
        }
        final List<Target> found = fitting.isEmpty() ? new ArrayList<>(targets) : fitting;
        found.removeIf(group -> found.stream().anyMatch(other -> within(plan, other.position, group.position)));
        return found;
    }

    /** Whether a row is under another, at any depth. */
    private static boolean within(final PlanStructure plan, final int row, final int group) {
        for (int above = plan.items().get(row).parent(); above >= 0; above = plan.items().get(above).parent()) {
            if (above == group) {
                return true;
            }
        }
        return false;
    }

    /** Names of the subject to compare: as written, as «Вид (тип)», without the elective note. */
    static List<String> variants(final String name) {
        final Set<String> variants = new LinkedHashSet<>();
        variants.add(PlanRow.key(name));
        final String bare = ELECTIVE_NOTE.matcher(name).replaceFirst("");
        variants.add(PlanRow.key(bare));
        final int dot = bare.indexOf(". ");
        if (dot > 0) {
            variants.add(PlanRow.key(PlanStructure.practice(bare.substring(0, dot), bare.substring(dot + 2))));
            variants.add(PlanRow.key(bare.substring(dot + 2)));
        }
        variants.remove("");
        return new ArrayList<>(variants);
    }

    /** Name keys of the elements a subject of a kind may go to: the name, the printed name, each elective. */
    private static Map<Target, List<String>> names(final PlanStructure plan, final Kind kind) {
        final Map<Target, List<String>> names = new LinkedHashMap<>();
        for (final PlanItem item : plan.items()) {
            if (!fits(item, kind)) {
                continue;
            }
            if (item.alternatives().isEmpty()) {
                names.put(
                    new Target(item.position(), ""),
                    Arrays.asList(PlanRow.key(item.row().name()), PlanRow.key(item.printed()))
                );
            }
            for (final String alternative : item.alternatives()) {
                names.put(new Target(item.position(), alternative), Collections.singletonList(PlanRow.key(alternative)));
            }
        }
        return names;
    }

    private static boolean fits(final PlanItem item, final Kind kind) {
        if (item.kind() != PlanItem.Kind.ELEMENT) {
            return false;
        }
        switch (item.section()) {
            case DISCIPLINES:
                return kind != Kind.PRACTICE;
            case FACULTATIVES:
                return kind == Kind.DISCIPLINE;
            case PRACTICES:
                return kind != Kind.COURSE_WORK;
            default:
                return false;
        }
    }

    private static boolean valid(final PlanStructure plan, final Subject subject, final Target target) {
        if (target.position < 0) {
            return true;
        }
        if (target.position >= plan.items().size() || !fits(plan.items().get(target.position), subject.kind)) {
            return false;
        }
        final List<String> alternatives = plan.items().get(target.position).alternatives();
        return alternatives.isEmpty() ? target.alternative.isEmpty() : alternatives.contains(target.alternative);
    }

    /** Subjects that put two records of one student on one element. */
    private static Set<String> conflicts(
        final StatementImport statement, final List<String> students, final Map<String, Target> targets
    ) {
        final Set<String> conflicts = new HashSet<>();
        for (final String student : students) {
            final Map<String, String> taken = new LinkedHashMap<>();
            for (final StudyRecord record : statement.records(student)) {
                if (record.kind() == Kind.UNKNOWN) {
                    continue;
                }
                final String key = Subject.key(record.name(), record.kind());
                final Target target = targets.get(key);
                if (target == null || target.position < 0) {
                    continue;
                }
                final String place = target.position + (record.kind() == Kind.COURSE_WORK ? ":курсовая" : "");
                final String other = taken.putIfAbsent(place, key);
                if (other != null && !other.equals(key)) {
                    conflicts.add(key);
                    conflicts.add(other);
                }
            }
        }
        return conflicts;
    }

    private static List<String> notes(final PlanStructure plan, final Subject subject, final Target target) {
        final List<String> notes = new ArrayList<>();
        if (target != null && target.position >= 0) {
            final PlanRow row = plan.items().get(target.position).row();
            if (subject.kind == Kind.COURSE_WORK) {
                if (!courseWork(row)) {
                    notes.add("в плане у элемента нет курсовой работы или проекта");
                }
            } else if (row.credits() == null) {
                notes.add("в плане без з.е.: в приложение не попадает");
            } else if (row.credits() != subject.counted && !courseWork(row)) {
                // the rows of a course work add their own hours to the discipline, so its sum is no check
                notes.add("по часам ведомости " + subject.counted + " з.е., в плане "
                    + (row.credits() == Math.rint(row.credits()) ? String.valueOf(row.credits().intValue())
                        : String.valueOf(row.credits())));
            }
        }
        if (subject.ungraded > 0) {
            notes.add("у " + subject.ungraded + " студ. в ведомости есть семестр без оценки");
        }
        return notes;
    }

    /** Whether a row has a course work or project in some semester. */
    static boolean courseWork(final PlanRow row) {
        return !row.controls().get(PlanRow.COURSE_WORKS).isEmpty()
            || !row.controls().get(PlanRow.COURSE_PROJECTS).isEmpty();
    }

    /** Shared words over all words of two keys. */
    private static double similarity(final String left, final String right) {
        final Set<String> a = new HashSet<>(Arrays.asList(left.split(" ")));
        final Set<String> b = new HashSet<>(Arrays.asList(right.split(" ")));
        final Set<String> all = new HashSet<>(a);
        all.addAll(b);
        a.retainAll(b);
        return all.isEmpty() ? 0 : (double) a.size() / all.size();
    }
}
