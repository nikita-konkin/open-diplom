package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.graduation.Edits;
import org.opendiplom.graduation.SubjectMatch;
import org.opendiplom.plans.PlanHeader;

/** Graduations from the staging zone to the registry, in SQLite. */
final class GraduationsTest {
    @TempDir
    Path folder;

    private Graduations graduations;
    private Curricula.Saved plan;

    @BeforeEach
    void open() throws Exception {
        final Database database = Database.open("jdbc:sqlite:" + this.folder.resolve("open-diplom.db"));
        this.graduations = new Graduations(database);
        this.plan = new Curricula(database).save(
            new PlanHeader("11.03.02", "Инфокоммуникационные технологии и системы связи", "Сети", "бакалавр", "очная",
                "4 года", "2021"),
            Plans.bachelor(), "xlsx", null, null, ""
        );
    }

    private static GraduateRecord graduate(final int position, final String last, final Integer grade) {
        return new GraduateRecord(
            null, position, last, "Иван", "Иванович", "2001-02-03", "Аттестат", "2019", "2026-06-24", "3", "Тема", 5, null,
            last + " И. И.", "1", "", Arrays.asList(
                new ResultRecord(2, ResultRecord.DISCIPLINE, "Математика", grade, grade == null ? "" : "5", 8.0),
                new ResultRecord(2, ResultRecord.COURSE_WORK, "Математика (курсовая работа)", 4, "4", null)
            )
        );
    }

    private String staged() throws Exception {
        final String id = this.graduations.stage("ИТС-41", "очная", 2021, "Ведомость.xlsx", "Сведения.xlsx");
        this.graduations.curriculum(id, this.plan.id);
        return id;
    }

    private String programId() throws Exception {
        return new Curricula(Database.open("jdbc:sqlite:" + this.folder.resolve("open-diplom.db")))
            .find(this.plan.id).programId;
    }

    @Test
    void cannotLoseResultsOfGraduate() throws Exception {
        final String id = this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Иванов", null)), this.programId(),
            Collections.emptyList()
        );
        final GraduateRecord read = this.graduations.graduates(id).get(0);
        assertEquals(
            "Иванов Иван Иванович: null «» 8.0, 4 null",
            read.fullName() + ": " + read.results.get(0).grade + " «" + read.results.get(0).gradeText + "» "
                + read.results.get(0).credits + ", " + read.results.get(1).grade + " " + read.results.get(1).credits,
            "A result without a grade or a course work without credits was not kept as it was"
        );
    }

    @Test
    void cannotKeepOldGraduatesOnImportOfSameGroup() throws Exception {
        final String first = this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Иванов", 3)), this.programId(), Collections.emptyList()
        );
        this.graduations.chairman(first, "Председатель П. П.");
        final String second = this.graduations.register(
            this.staged(), Arrays.asList(graduate(0, "Иванов", 5), graduate(1, "Петров", 4)), this.programId(),
            Collections.emptyList()
        );
        final List<GraduateRecord> graduates = this.graduations.graduates(second);
        assertEquals(
            "true 2 5 Председатель П. П. 1",
            first.equals(second) + " " + graduates.size() + " " + graduates.get(0).results.get(0).grade + " "
                + this.graduations.find(second).gekChairman + " " + this.graduations.all().size(),
            "Loading the group again did not replace its graduates in place, or lost the chairman"
        );
    }

    @Test
    void cannotForgetLinksOfProgram() throws Exception {
        final SubjectMatch.Link link = new SubjectMatch.Link("дисциплина:астрономия", "Б.1.1.2", "физика", "");
        this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Иванов", 5)), this.programId(),
            Collections.singletonList(link)
        );
        final Map<String, SubjectMatch.Link> links = this.graduations.links(this.programId());
        assertEquals(
            "Б.1.1.2 физика", links.get("дисциплина:астрономия").elementIndex + " "
                + links.get("дисциплина:астрономия").elementKey,
            "The link the operator confirmed was not kept for the program"
        );
    }

    @Test
    void cannotAskAgainAboutStudentsOnNextLoad() throws Exception {
        final String first = this.staged();
        this.graduations.choose(first, Map.of(
            "info:иванов иван", "Иванов И. П.", "sheet:Иванов И. И.", "-", "subject:дисциплина:астрономия", "-"
        ), null);
        final String registered = this.graduations.register(
            first, Collections.singletonList(graduate(0, "Иванов", 5)), this.programId(), Collections.emptyList()
        );
        final String again = this.staged();
        final Map<String, String> inherited = this.graduations.choices(again);
        this.graduations.choose(again, Map.of("sheet:Иванов И. И.", ""), null);
        this.graduations.register(
            again, Collections.singletonList(graduate(0, "Иванов", 5)), this.programId(), Collections.emptyList()
        );
        assertEquals(
            "{info:иванов иван=Иванов И. П., sheet:Иванов И. И.=-} {info:иванов иван=Иванов И. П.}",
            new TreeMap<>(inherited) + " " + this.graduations.choices(registered),
            "The choices about students did not come to the next loading of the group, or the new ones did not replace them"
        );
    }

    private static Edits.Edit edit(final String field, final Edits.Place place, final String value, final String original) {
        return new Edits.Edit("", field, place, place == null ? "" : "Математика", value, original, "2026-07-01T09:00:00Z");
    }

    private static String fields(final List<Edits.Edit> edits) {
        final List<String> fields = new ArrayList<>();
        for (final Edits.Edit edit : edits) {
            fields.add(edit.field + "=" + edit.value);
        }
        return fields.toString();
    }

    @Test
    void cannotLoseCorrectionsOnImportOfSameGroup() throws Exception {
        final String id = this.graduations.register(
            this.staged(), Arrays.asList(graduate(0, "Иванов", 3), graduate(1, "Петров", 4)), this.programId(),
            Collections.emptyList()
        );
        final List<GraduateRecord> first = this.graduations.graduates(id);
        this.graduations.edit(id, first.get(0).id, first.get(0).fullName(), Arrays.asList(
            edit("gek_protocol", null, "7", "3"), edit(Edits.GRADE, new Edits.Place("Б.1.1.1", "математика", "", false), "5", "3")
        ), Collections.emptyList());
        this.graduations.edit(id, first.get(1).id, first.get(1).fullName(),
            Collections.singletonList(edit("gek_protocol", null, "8", "3")), Collections.emptyList());
        this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Иванов", 4)), this.programId(), Collections.emptyList()
        );
        final Map<String, List<Edits.Edit>> edits = this.graduations.edits(id);
        assertEquals(
            "1 [gek_protocol=7, grade=5] true",
            edits.size() + " " + fields(edits.get(first.get(0).id)) + " "
                + this.graduations.history(id).stream().anyMatch(entry -> entry.contains(
                    "правка в карточке выпускника — Иванов Иван Иванович: Номер протокола ГЭК: «3» → «7»")),
            "The corrections of a graduate loaded again were lost, or of one no longer in the files kept, "
                + "or not written in the journal"
        );
        final Edits.Edit protocol = edits.get(first.get(0).id).get(0);
        this.graduations.edit(id, first.get(0).id, first.get(0).fullName(), Collections.emptyList(),
            Collections.singletonList(protocol));
        assertEquals("[grade=5]", fields(this.graduations.edits(id).get(first.get(0).id)),
            "A correction taken off is still there");
    }

    @Test
    void cannotLoseGraduateWhoseNameOperatorCorrected() throws Exception {
        final String id = this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Ивонов", 5)), this.programId(), Collections.emptyList()
        );
        final String graduate = this.graduations.graduates(id).get(0).id;
        this.graduations.edit(id, graduate, "Ивонов Иван Иванович",
            Collections.singletonList(edit("last_name", null, "Иванов", "Ивонов")), Collections.emptyList());
        // the dean's office corrected the file the same way
        this.graduations.register(
            this.staged(), Collections.singletonList(graduate(0, "Иванов", 5)), this.programId(), Collections.emptyList()
        );
        assertEquals(
            graduate + " Иванов 1", this.graduations.graduates(id).get(0).id + " "
                + this.graduations.graduates(id).get(0).lastName + " " + this.graduations.edits(id).size(),
            "A graduate whose name came corrected in the files as the operator corrected it was taken for another"
        );
    }

    @Test
    void cannotLeaveDeletedStagingBehind() throws Exception {
        final String id = this.staged();
        this.graduations.choose(id, Map.of("info:2", "-"), null);
        this.graduations.delete(id);
        assertNull(this.graduations.find(id), "A graduation deleted from the staging zone is still there");
    }
}
