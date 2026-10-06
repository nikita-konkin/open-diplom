package org.opendiplom.graduation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;

/**
 * Corrections the operator makes in the card of a graduate (ADR-0012), laid
 * over what the files gave. They are kept apart from the files: the group
 * loaded again keeps them, and a file changed since a correction shows
 * against it.
 *
 * <p>A correction of a result knows its element as a subject link does — by
 * the index, the name and the chosen elective — so it finds the element in
 * another edition of the plan. A grade for an element without a result adds
 * the result: the grade the statement lacks.
 */
public final class Edits {
    public static final String GRADE = "grade";
    public static final String CREDITS = "credits";
    public static final String KIND = "kind";

    /** Columns of the graduate the card corrects, with their titles, in the order of the card. */
    public static final Map<String, String> COLUMNS = columns(
        "last_name", "Фамилия", "first_name", "Имя", "middle_name", "Отчество", "birth_date", "Дата рождения",
        "previous_document", "Документ о предыдущем образовании", "previous_year", "Год документа",
        "gek_date", "Дата решения ГЭК", "gek_protocol", "Номер протокола ГЭК",
        "state_exam_grade", "Государственный экзамен", "thesis_topic", "Тема ВКР", "thesis_grade", "Оценка ВКР"
    );
    /** Columns holding a grade code. */
    public static final Set<String> GRADES = Set.of("thesis_grade", "state_exam_grade");
    /** Columns holding a date ГГГГ-ММ-ДД. */
    public static final Set<String> DATES = Set.of("birth_date", "gek_date");

    private static final Map<String, String> RESULT_FIELDS = columns(GRADE, "оценка", CREDITS, "з.е.", KIND, "вид");
    /** A grade before the credits and the kind: it may add the result they correct. */
    private static final List<String> ORDER = Arrays.asList(GRADE, CREDITS, KIND);
    private static final Pattern CODE = Pattern.compile("[2-7]");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private Edits() {
    }

    /** Where a correction of a result belongs: an element of the plan, or the course work of one. */
    public static final class Place {
        public final String elementIndex;
        public final String elementKey;
        public final String alternativeKey;
        public final boolean courseWork;

        public Place(
            final String elementIndex, final String elementKey, final String alternativeKey, final boolean courseWork
        ) {
            this.elementIndex = elementIndex;
            this.elementKey = elementKey;
            this.alternativeKey = alternativeKey;
            this.courseWork = courseWork;
        }

        /** The place of a result on its element. */
        public static Place of(final PlanStructure plan, final ResultRecord result) {
            final PlanItem item = plan.items().get(result.element);
            final boolean work = ResultRecord.COURSE_WORK.equals(result.kind);
            final String suffix = Results.work(item.row());
            String alternative = "";
            if (!item.alternatives().isEmpty()) {
                alternative = work && result.printed.endsWith(suffix)
                    ? result.printed.substring(0, result.printed.length() - suffix.length()) : result.printed;
            }
            return new Place(item.row().index(), PlanRow.key(item.row().name()), PlanRow.key(alternative), work);
        }

        /** The place of an element without electives, or of its course work. */
        public static Place of(final PlanItem item, final boolean courseWork) {
            return new Place(item.row().index(), PlanRow.key(item.row().name()), "", courseWork);
        }

        /** The element in a plan, {@code null} when the plan has none such. */
        SubjectMatch.Target target(final PlanStructure plan) {
            return SubjectMatch.remembered(
                plan, new SubjectMatch.Link("", this.elementIndex, this.elementKey, this.alternativeKey)
            );
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof Place)) {
                return false;
            }
            final Place place = (Place) other;
            return place.elementIndex.equals(this.elementIndex) && place.elementKey.equals(this.elementKey)
                && place.alternativeKey.equals(this.alternativeKey) && place.courseWork == this.courseWork;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.elementIndex, this.elementKey, this.alternativeKey, this.courseWork);
        }
    }

    /** One correction. */
    public static final class Edit {
        /** Empty for one not kept yet. */
        public final String id;
        /** A column of {@link #COLUMNS}; of a result, {@link #GRADE}, {@link #CREDITS} or {@link #KIND}. */
        public final String field;
        /** {@code null} for a column of the graduate. */
        public final Place place;
        /** The name of the result as the card showed it; empty for a column. */
        public final String printed;
        public final String value;
        /** What the files had when the correction was made. */
        public final String original;
        public final String editedAt;

        public Edit(
            final String id, final String field, final Place place, final String printed, final String value,
            final String original, final String editedAt
        ) {
            this.id = id;
            this.field = field;
            this.place = place;
            this.printed = printed;
            this.value = value;
            this.original = original;
            this.editedAt = editedAt;
        }

        /** «Номер протокола ГЭК», «"Физика": оценка». */
        public String title() {
            return this.place == null ? COLUMNS.getOrDefault(this.field, this.field)
                : "«" + this.printed + "»: " + RESULT_FIELDS.getOrDefault(this.field, this.field);
        }

        /** Whether it corrects what another does. */
        boolean same(final String field, final Place place) {
            return this.field.equals(field) && Objects.equals(this.place, place);
        }
    }

    /** A graduate as the files gave it with the corrections over it, and what the operator should know about them. */
    public static final class Applied {
        public final GraduateRecord graduate;
        /** Corrections the files contradict now, or that found no place. */
        public final List<String> notes;

        Applied(final GraduateRecord graduate, final List<String> notes) {
            this.graduate = graduate;
            this.notes = Collections.unmodifiableList(notes);
        }
    }

    /** Corrections to keep and to take off. */
    public static final class Changes {
        public final List<Edit> kept;
        public final List<Edit> removed;

        public Changes(final List<Edit> kept, final List<Edit> removed) {
            this.kept = Collections.unmodifiableList(kept);
            this.removed = Collections.unmodifiableList(removed);
        }
    }

    /** Lays the corrections over a graduate as the files gave it. */
    public static Applied apply(final GraduateRecord files, final PlanStructure plan, final List<Edit> edits) {
        final Map<String, String> columns = new LinkedHashMap<>();
        for (final String column : COLUMNS.keySet()) {
            columns.put(column, column(files, column));
        }
        final List<ResultRecord> results = new ArrayList<>(files.results);
        final List<String> notes = new ArrayList<>();
        final List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(edit -> ORDER.indexOf(edit.field)));
        for (final Edit edit : ordered) {
            if (edit.place == null) {
                if (columns.containsKey(edit.field)) {
                    check(edit, columns.get(edit.field), notes);
                    columns.put(edit.field, edit.value);
                }
                continue;
            }
            final SubjectMatch.Target target = edit.place.target(plan);
            final int found = target == null ? -1 : find(results, target.position, edit.place.courseWork);
            if (target == null || found < 0 && !GRADE.equals(edit.field)) {
                unapplied(edit, target == null ? "элемента нет в учебном плане выпуска" : "у выпускника нет этого результата",
                    notes);
                continue;
            }
            if (found < 0) {
                if (!edit.value.isEmpty()) {
                    results.add(added(plan.items().get(target.position), target.alternative, edit));
                }
                continue;
            }
            final ResultRecord result = results.get(found);
            if (GRADE.equals(edit.field)) {
                check(edit, grade(result), notes);
                results.set(found, new ResultRecord(
                    result.element, result.kind, result.printed, code(edit.value), edit.value, result.credits
                ));
            } else if (CREDITS.equals(edit.field) && result.credits != null) {
                check(edit, PlanTotals.number(result.credits), notes);
                results.set(found, new ResultRecord(
                    result.element, result.kind, result.printed, result.grade, result.gradeText, number(edit.value)
                ));
            } else if (KIND.equals(edit.field) && movable(result.kind)) {
                check(edit, result.kind, notes);
                results.set(found, new ResultRecord(
                    result.element, edit.value, result.printed, result.grade, result.gradeText, result.credits
                ));
            } else {
                unapplied(edit, "не подходит к результату вида «" + result.kind + "»", notes);
            }
        }
        results.sort(Comparator.comparingInt((ResultRecord result) -> result.element)
            .thenComparing(result -> ResultRecord.COURSE_WORK.equals(result.kind)));
        return new Applied(new GraduateRecord(
            files.id, files.position, columns.get("last_name"), columns.get("first_name"), columns.get("middle_name"),
            columns.get("birth_date"), columns.get("previous_document"), columns.get("previous_year"),
            columns.get("gek_date"), columns.get("gek_protocol"), columns.get("thesis_topic"),
            code(columns.get("thesis_grade")), code(columns.get("state_exam_grade")), files.statementName,
            files.studentNumber, files.notes, results
        ), notes);
    }

    /**
     * What the card sent, against the graduate as it is: a value set back to
     * what the files have takes its correction off, another value is kept as a
     * correction. A result gets a grade, credits and the kind of a discipline
     * or a facultative; an element without a result, only a grade.
     *
     * @param files the graduate as the files gave it
     * @param edited the same with the corrections, {@link #apply}
     * @param form the values by {@link #name}, and by column for the columns
     * @throws IllegalArgumentException when a value is not one the field takes
     */
    public static Changes changes(
        final GraduateRecord files, final GraduateRecord edited, final PlanStructure plan, final List<Edit> edits,
        final Map<String, String> form, final String now
    ) {
        final List<Edit> kept = new ArrayList<>();
        final List<Edit> removed = new ArrayList<>();
        final Changing changing = new Changing(edits, form, now, kept, removed);
        for (final Map.Entry<String, String> column : COLUMNS.entrySet()) {
            changing.change(column.getKey(), null, "", column.getKey(), column(edited, column.getKey()),
                column(files, column.getKey()));
        }
        for (final ResultRecord result : edited.results) {
            final Place place = Place.of(plan, result);
            final boolean work = ResultRecord.COURSE_WORK.equals(result.kind);
            final int found = find(files.results, result.element, work);
            final ResultRecord file = found < 0 ? null : files.results.get(found);
            changing.change(name(result.element, work, GRADE), place, result.printed, GRADE, coded(grade(result)),
                file == null ? "" : grade(file));
            if (file != null && result.credits != null) {
                changing.change(name(result.element, false, CREDITS), place, result.printed, CREDITS,
                    PlanTotals.number(result.credits), file.credits == null ? "" : PlanTotals.number(file.credits));
            }
            if (file != null && movable(result.kind)) {
                changing.change(name(result.element, false, KIND), place, result.printed, KIND, result.kind, file.kind);
            }
        }
        for (final Checks.Missing missing : Checks.missing(edited, plan)) {
            if (missing.item.alternatives().isEmpty()) {
                changing.change(name(missing.item.position(), missing.courseWork, GRADE),
                    Place.of(missing.item, missing.courseWork), printed(missing.item, "", missing.courseWork), GRADE,
                    "", "");
            }
        }
        return new Changes(kept, removed);
    }

    /** The name of a field of a result in the form of the card: «r.12.d.grade», «r.12.w.grade» of its course work. */
    public static String name(final int position, final boolean courseWork, final String field) {
        return "r." + position + "." + (courseWork ? "w" : "d") + "." + field;
    }

    /** The value of a column of a graduate, a grade as its code; empty for none. */
    public static String column(final GraduateRecord graduate, final String column) {
        switch (column) {
            case "last_name":
                return graduate.lastName;
            case "first_name":
                return graduate.firstName;
            case "middle_name":
                return graduate.middleName;
            case "birth_date":
                return graduate.birthDate;
            case "previous_document":
                return graduate.previousDocument;
            case "previous_year":
                return graduate.previousYear;
            case "gek_date":
                return graduate.gekDate;
            case "gek_protocol":
                return graduate.gekProtocol;
            case "thesis_topic":
                return graduate.thesisTopic;
            case "thesis_grade":
                return graduate.thesisGrade == null ? "" : String.valueOf(graduate.thesisGrade);
            case "state_exam_grade":
                return graduate.stateExamGrade == null ? "" : String.valueOf(graduate.stateExamGrade);
            default:
                throw new IllegalArgumentException("Нет поля выпускника «" + column + "»");
        }
    }

    /** The grade of a result as the statement has it: the code, or the text that is not one. */
    public static String grade(final ResultRecord result) {
        return result.grade == null ? result.gradeText : String.valueOf(result.grade);
    }

    /** Whether a result of the kind may become the other of a discipline and a facultative. */
    public static boolean movable(final String kind) {
        return ResultRecord.DISCIPLINE.equals(kind) || ResultRecord.FACULTATIVE.equals(kind);
    }

    /** The name a result on the element prints. */
    public static String printed(final PlanItem item, final String alternative, final boolean courseWork) {
        final String name = alternative.isEmpty() ? item.printed() : alternative;
        return courseWork ? name + Results.work(item.row()) : name;
    }

    /** The comparison of the form with the graduate, field by field. */
    private static final class Changing {
        private final List<Edit> edits;
        private final Map<String, String> form;
        private final String now;
        private final List<Edit> kept;
        private final List<Edit> removed;

        Changing(
            final List<Edit> edits, final Map<String, String> form, final String now, final List<Edit> kept,
            final List<Edit> removed
        ) {
            this.edits = edits;
            this.form = form;
            this.now = now;
            this.kept = kept;
            this.removed = removed;
        }

        /**
         * @param current the value the card showed
         * @param file what the files have, as the statement writes it
         */
        void change(
            final String name, final Place place, final String printed, final String field, final String current,
            final String file
        ) {
            final String sent = this.form.get(name);
            if (sent == null) {
                return;
            }
            final String value = valid(field, place, printed, sent.strip());
            if (value.equals(current)) {
                return;
            }
            Edit earlier = null;
            for (final Edit edit : this.edits) {
                if (edit.same(field, place)) {
                    earlier = edit;
                }
            }
            final String plain = GRADE.equals(field) || GRADES.contains(field) ? coded(file) : file;
            if (value.equals(plain)) {
                if (earlier != null) {
                    this.removed.add(earlier);
                }
                return;
            }
            this.kept.add(new Edit(earlier == null ? "" : earlier.id, field, place, printed, value, file, this.now));
        }
    }

    /** A value of a field as kept, or why it is not one. */
    private static String valid(final String field, final Place place, final String printed, final String value) {
        final String title = place == null ? COLUMNS.get(field) : "«" + printed + "»: " + RESULT_FIELDS.get(field);
        if ((GRADE.equals(field) || GRADES.contains(field)) && !value.isEmpty() && !CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(title + " «" + value + "»: нужен код оценки 2–7");
        }
        if (DATES.contains(field) && !value.isEmpty() && !DATE.matcher(value).matches()) {
            throw new IllegalArgumentException(title + " «" + value + "»: нужна дата ГГГГ-ММ-ДД");
        }
        if (("last_name".equals(field) || "first_name".equals(field)) && value.isEmpty()) {
            throw new IllegalArgumentException(title + ": не может быть пустым");
        }
        if (CREDITS.equals(field)) {
            final Double number;
            try {
                number = Double.valueOf(value.replace(',', '.'));
            } catch (final NumberFormatException error) {
                throw new IllegalArgumentException(title + " «" + value + "»: нужно число", error);
            }
            if (!(number > 0) || number.isInfinite()) {
                throw new IllegalArgumentException(title + " «" + value + "»: нужно положительное число");
            }
            return PlanTotals.number(number);
        }
        if (KIND.equals(field) && !movable(value)) {
            throw new IllegalArgumentException(title + " «" + value + "»: дисциплина или факультатив");
        }
        return value;
    }

    /** Notes a correction whose field the files have changed since. */
    private static void check(final Edit edit, final String current, final List<String> notes) {
        if (!current.equals(edit.original) && !current.equals(edit.value)) {
            notes.add("«" + edit.title() + "»: после правки в файлах " + shown(current) + ", а не " + shown(edit.original)
                + "; осталась правка " + shown(edit.value));
        }
    }

    private static void unapplied(final Edit edit, final String reason, final List<String> notes) {
        notes.add(edit.title() + ": правка " + shown(edit.value) + " не применена: " + reason);
    }

    private static String shown(final String value) {
        return value.isEmpty() ? "пусто" : "«" + value + "»";
    }

    /** A result the statement lacks, from the element and the grade of a correction. */
    private static ResultRecord added(final PlanItem item, final String alternative, final Edit edit) {
        final boolean work = edit.place.courseWork;
        return new ResultRecord(
            item.position(), work ? ResultRecord.COURSE_WORK : Results.kind(item), printed(item, alternative, work),
            code(edit.value), edit.value, work ? null : item.row().credits()
        );
    }

    /** The result of an element, or of its course work; -1 when there is none. */
    private static int find(final List<ResultRecord> results, final int position, final boolean courseWork) {
        for (int index = 0; index < results.size(); ++index) {
            final ResultRecord result = results.get(index);
            if (result.element == position && ResultRecord.COURSE_WORK.equals(result.kind) == courseWork) {
                return index;
            }
        }
        return -1;
    }

    /** A grade code as it is, empty for a text that is not one. */
    private static String coded(final String value) {
        return CODE.matcher(value).matches() ? value : "";
    }

    private static Integer code(final String value) {
        return value != null && CODE.matcher(value).matches() ? Integer.valueOf(value) : null;
    }

    private static Double number(final String value) {
        return Double.valueOf(value.replace(',', '.'));
    }

    private static Map<String, String> columns(final String... pairs) {
        final Map<String, String> columns = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            columns.put(pairs[index], pairs[index + 1]);
        }
        return Collections.unmodifiableMap(columns);
    }
}
