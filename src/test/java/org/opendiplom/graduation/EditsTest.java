package org.opendiplom.graduation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.opendiplom.Plans;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;

/** Corrections of the operator over what the files gave; the plan is {@link Plans#bachelor()}. */
final class EditsTest {
    private static final String NOW = "2026-07-01T09:00:00Z";
    private static final PlanStructure PLAN = PlanStructure.of(Plans.bachelor());
    private static final ResultRecord MATHS = new ResultRecord(2, ResultRecord.DISCIPLINE, "Математика", 5, "5", 8.0);
    private static final ResultRecord MATHS_WORK =
        new ResultRecord(2, ResultRecord.COURSE_WORK, "Математика (курсовая работа)", 4, "4", null);
    private static final ResultRecord PHYSICS = new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", 6, "6", 4.0);
    private static final ResultRecord GAMES = new ResultRecord(6, ResultRecord.FACULTATIVE, "Теория игр", 6, "6", 2.0);
    private static final String PRACTICE = "Производственная практика (преддипломная практика)";

    private static GraduateRecord graduate(final String protocol, final ResultRecord... results) {
        return new GraduateRecord(
            "g", 0, "Иванов", "Иван", "Иванович", "2001-02-03", "Аттестат", "2019", "2026-06-24", protocol, "Тема", 5, 5,
            "Иванов И. И.", "1", "", Arrays.asList(results)
        );
    }

    private static Edits.Edit edit(final String field, final Edits.Place place, final String value, final String original) {
        return new Edits.Edit("e-" + field, field, place, place == null ? "" : "Предмет", value, original, NOW);
    }

    /** The form as a browser sends the card unchanged. */
    private static Map<String, String> form(final GraduateRecord graduate) {
        final Map<String, String> form = new HashMap<>();
        for (final String column : Edits.COLUMNS.keySet()) {
            form.put(column, Edits.column(graduate, column));
        }
        for (final ResultRecord result : graduate.results) {
            final boolean work = ResultRecord.COURSE_WORK.equals(result.kind);
            form.put(Edits.name(result.element, work, Edits.GRADE), result.grade == null ? "" : String.valueOf(result.grade));
            if (!work) {
                form.put(Edits.name(result.element, false, Edits.CREDITS), PlanTotals.number(result.credits));
                form.put(Edits.name(result.element, false, Edits.KIND), result.kind);
            }
        }
        for (final Checks.Missing missing : Checks.missing(graduate, PLAN)) {
            form.put(Edits.name(missing.item.position(), missing.courseWork, Edits.GRADE), "");
        }
        return form;
    }

    private static String described(final Edits.Changes changes) {
        final List<String> described = new ArrayList<>();
        for (final Edits.Edit edit : changes.kept) {
            described.add("+" + edit.title() + " " + edit.original + "→" + edit.value + (edit.id.isEmpty() ? "" : " " + edit.id));
        }
        for (final Edits.Edit edit : changes.removed) {
            described.add("-" + edit.id);
        }
        return described.toString();
    }

    @Test
    void cannotLetChangedFileSilentlyMeetCorrection() {
        final List<Edits.Edit> edits = Collections.singletonList(edit("gek_protocol", null, "7", "3"));
        final List<String> seen = new ArrayList<>();
        for (final String file : Arrays.asList("3", "123456", "7")) {
            final Edits.Applied applied = Edits.apply(graduate(file, MATHS), PLAN, edits);
            seen.add(applied.graduate.gekProtocol + " " + applied.notes);
        }
        assertEquals(
            "[7 [], 7 [«Номер протокола ГЭК»: после правки в файлах «123456», а не «3»; осталась правка «7»], 7 []]",
            seen.toString(),
            "A correction did not hold over the files, or a file changed since it went unnoticed"
        );
    }

    @Test
    void cannotLeaveGradeStatementLacks() {
        final Edits.Edit grade = edit(Edits.GRADE, Edits.Place.of(PLAN.items().get(10), false), "5", "");
        final Edits.Applied applied = Edits.apply(graduate("3", MATHS, MATHS_WORK, PHYSICS), PLAN, Collections.singletonList(grade));
        final ResultRecord added = applied.graduate.results.get(applied.graduate.results.size() - 1);
        final List<Checks.Finding> findings = Checks.of(
            applied.graduate, PLAN, PlanTotals.of(Plans.bachelor()), applied.notes
        );
        assertEquals(
            "10 практика «" + PRACTICE + "» 5 6.0 []",
            added.element + " " + added.kind + " «" + added.printed + "» " + added.grade + " " + added.credits + " " + findings,
            "A grade the operator gave for a practice the statement lacks did not make the result the plan asks"
        );
    }

    @Test
    void cannotLoseElementInAnotherEdition() {
        final Edits.Place physics = Edits.Place.of(PLAN, PHYSICS);
        final Edits.Place games = Edits.Place.of(PLAN, GAMES);
        final List<PlanRow> rows = Plans.bachelor();
        rows.add(3, Plans.row("Б.1.1.4", "Химия", ";2;;;", "4 - 4", "144 - 144 50 94"));
        rows.removeIf(row -> "Теория игр".equals(row.name()));
        final PlanStructure next = PlanStructure.of(rows);
        final ResultRecord moved = new ResultRecord(4, ResultRecord.DISCIPLINE, "Физика", 6, "6", 4.0);
        final Edits.Applied applied = Edits.apply(graduate("3", MATHS, moved), next, Arrays.asList(
            edit(Edits.GRADE, physics, "5", "6"), edit(Edits.GRADE, games, "5", "6")
        ));
        assertEquals(
            "4 Физика 5 [WARNING «Предмет»: оценка: правка «5» не применена: элемента нет в учебном плане выпуска]",
            applied.graduate.results.get(1).element + " " + applied.graduate.results.get(1).printed + " "
                + applied.graduate.results.get(1).grade + " " + Checks.of(applied.graduate, next, PlanTotals.of(rows),
                    applied.notes).stream().filter(finding -> finding.level == Checks.Level.WARNING)
                    .map(Checks.Finding::toString).collect(Collectors.toList()),
            "A correction did not find its element in another edition of the plan, or one without it went unnoticed"
        );
    }

    @Test
    void cannotMoveCourseWorkToFacultatives() {
        final Edits.Applied applied = Edits.apply(graduate("3", MATHS, MATHS_WORK, PHYSICS), PLAN, Arrays.asList(
            edit(Edits.KIND, Edits.Place.of(PLAN, PHYSICS), ResultRecord.FACULTATIVE, ResultRecord.DISCIPLINE),
            edit(Edits.KIND, Edits.Place.of(PLAN, MATHS_WORK), ResultRecord.FACULTATIVE, ResultRecord.COURSE_WORK)
        ));
        assertEquals(
            "[дисциплина, курсовая, факультатив] [«Предмет»: вид: правка «факультатив» не применена: "
                + "не подходит к результату вида «курсовая»]",
            Arrays.asList(applied.graduate.results.get(0).kind, applied.graduate.results.get(1).kind,
                applied.graduate.results.get(2).kind) + " " + applied.notes,
            "A discipline studied beyond the choice did not become a facultative, or a course work did"
        );
    }

    @Test
    void cannotTakeUnchangedCardForCorrection() {
        final ResultRecord unreadable = new ResultRecord(3, ResultRecord.DISCIPLINE, "Физика", null, "зач", 4.0);
        final GraduateRecord files = graduate("3", MATHS, MATHS_WORK, unreadable);
        final List<Edits.Edit> edits = Arrays.asList(
            edit("gek_protocol", null, "7", "3"), edit(Edits.GRADE, Edits.Place.of(PLAN.items().get(10), false), "5", "")
        );
        final GraduateRecord edited = Edits.apply(files, PLAN, edits).graduate;
        assertEquals(
            "[]", described(Edits.changes(files, edited, PLAN, edits, form(edited), NOW)),
            "The card sent back unchanged made corrections"
        );
    }

    @Test
    void cannotKeepCorrectionSetBackToFile() {
        final GraduateRecord files = graduate("3", MATHS, MATHS_WORK, PHYSICS);
        final Edits.Edit protocol = edit("gek_protocol", null, "7", "3");
        final Edits.Edit credits = edit(Edits.CREDITS, Edits.Place.of(PLAN, PHYSICS), "5", "4");
        final List<Edits.Edit> edits = Arrays.asList(protocol, credits);
        final GraduateRecord edited = Edits.apply(files, PLAN, edits).graduate;
        final Map<String, String> form = form(edited);
        form.put("gek_protocol", " 3 ");
        form.put(Edits.name(3, false, Edits.CREDITS), "4,5");
        form.put(Edits.name(10, false, Edits.GRADE), "6");
        form.put(Edits.name(2, true, Edits.GRADE), "5");
        assertEquals(
            "[+«Математика (курсовая работа)»: оценка 4→5, +«Физика»: з.е. 4→4,5 e-credits, "
                + "+«" + PRACTICE + "»: оценка →6, -e-gek_protocol]",
            described(Edits.changes(files, edited, PLAN, edits, form, NOW)),
            "A value set back to the files kept its correction, or a changed one was not kept in place of the earlier"
        );
    }

    @Test
    void cannotTakeValueFieldDoesNotHold() {
        final GraduateRecord files = graduate("3", MATHS, MATHS_WORK, PHYSICS);
        final List<String> refused = new ArrayList<>();
        for (final String[] wrong : new String[][] {
            {"thesis_grade", "8"}, {"gek_date", "24.06.2026"}, {"last_name", " "},
            {Edits.name(3, false, Edits.CREDITS), "0"}, {Edits.name(3, false, Edits.CREDITS), "четыре"},
            {Edits.name(3, false, Edits.KIND), ResultRecord.PRACTICE},
        }) {
            final Map<String, String> form = form(files);
            form.put(wrong[0], wrong[1]);
            refused.add(assertThrows(IllegalArgumentException.class,
                () -> Edits.changes(files, files, PLAN, Collections.emptyList(), form, NOW),
                "A wrong value was taken: " + Arrays.toString(wrong)).getMessage());
        }
        assertTrue(
            refused.get(0).contains("нужен код оценки 2–7") && refused.get(1).contains("нужна дата ГГГГ-ММ-ДД")
                && refused.get(2).contains("не может быть пустым") && refused.get(3).contains("положительное число")
                && refused.get(4).contains("нужно число") && refused.get(5).contains("дисциплина или факультатив"),
            "A wrong value was refused without saying what the field takes: " + refused
        );
    }
}
