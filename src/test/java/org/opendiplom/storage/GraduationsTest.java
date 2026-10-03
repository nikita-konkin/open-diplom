package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.Arrays;
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

    @Test
    void cannotLeaveDeletedStagingBehind() throws Exception {
        final String id = this.staged();
        this.graduations.choose(id, Map.of("info:2", "-"), null);
        this.graduations.delete(id);
        assertNull(this.graduations.find(id), "A graduation deleted from the staging zone is still there");
    }
}
