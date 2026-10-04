package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Organization;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.plans.PlanHeader;

/** Documents of the graduates and the organization, in SQLite (ADR-0010). */
final class DocumentsTest {
    @TempDir
    Path folder;

    private Database database;
    private Graduations graduations;
    private Documents documents;
    private Curricula.Saved plan;

    @BeforeEach
    void open() throws Exception {
        this.database = Database.open("jdbc:sqlite:" + this.folder.resolve("open-diplom.db"));
        this.graduations = new Graduations(this.database);
        this.documents = new Documents(this.database);
        this.plan = new Curricula(this.database).save(
            new PlanHeader("11.03.02", "Инфокоммуникационные технологии и системы связи", "Сети", "бакалавр", "очная",
                "4 года", "2021"),
            Plans.bachelor(), "xlsx", null, null, ""
        );
    }

    private static GraduateRecord graduate(final int position, final String last) {
        return new GraduateRecord(
            null, position, last, "Иван", "Иванович", "2001-02-03", "Аттестат", "2019", "2026-06-24", "3", "Тема", 5, null,
            last + " И. И.", "1", "", Collections.singletonList(
                new ResultRecord(2, ResultRecord.DISCIPLINE, "Математика", 5, "5", 8.0)
            )
        );
    }

    /** A registered graduation of a group with graduates of these surnames. */
    private String registered(final String group, final String... surnames) throws Exception {
        final String id = this.graduations.stage(group, "очная", 2021, "Ведомость.xlsx", "Сведения.xlsx");
        this.graduations.curriculum(id, this.plan.id);
        final List<GraduateRecord> graduates = new ArrayList<>();
        for (final String surname : surnames) {
            graduates.add(graduate(graduates.size(), surname));
        }
        return this.graduations.register(
            id, graduates, new Curricula(this.database).find(this.plan.id).programId, Collections.emptyList()
        );
    }

    private List<String> numbers(final String graduation) throws Exception {
        final Map<String, List<DocumentRecord>> all = this.documents.of(graduation);
        final List<String> numbers = new ArrayList<>();
        for (final GraduateRecord graduate : this.graduations.graduates(graduation)) {
            final DocumentRecord original = Documents.original(all, graduate.id);
            numbers.add(original.regNumber + " " + original.issueDate);
        }
        return numbers;
    }

    @Test
    void cannotSkipGraduateOrNumber() throws Exception {
        final String graduation = this.registered("ИТС-41", "Андреев", "Борисов", "Васильев", "Григорьев");
        final List<GraduateRecord> graduates = this.graduations.graduates(graduation);
        this.documents.save(graduation, new DocumentRecord(
            null, graduates.get(1).id, null, false, false, "10990", "", null
        ), "вручную");
        final Documents.Given given = this.documents.give(
            graduation, graduates, Arrays.asList("10001", "10002", "10010", "10011"), "2026-07-03"
        );
        assertEquals(
            "[10001 2026-07-03, 10990 2026-07-03, 10002 2026-07-03, 10010 2026-07-03] 3 [10011] 0",
            this.numbers(graduation) + " " + given.numbered + " " + given.left + " " + given.wanting,
            "The numbers did not go in order to the graduates without one, or the date of issue was not set"
        );
    }

    @Test
    void cannotGiveNumberTwice() throws Exception {
        final String first = this.registered("ИТС-41", "Андреев");
        this.documents.give(first, this.graduations.graduates(first), Collections.singletonList("10001"), "");
        final String second = this.registered("ИСТ-43", "Борисов");
        assertThrows(
            IllegalArgumentException.class,
            () -> this.documents.give(second, this.graduations.graduates(second), Collections.singletonList("10001"), ""),
            "A number of one graduation was given in another"
        );
    }

    @Test
    void cannotLoseDocumentOnReload() throws Exception {
        final String graduation = this.registered("ИТС-41", "Андреев", "Борисов");
        this.documents.give(graduation, this.graduations.graduates(graduation), Arrays.asList("10001", "10002"),
            "2026-07-03");
        final String again = this.registered("ИТС-41", "Васильев", "Андреев");
        final List<String> after = this.numbers(again);
        this.documents.give(again, this.graduations.graduates(again), Collections.singletonList("10002"), "");
        assertEquals(
            graduation + " [ , 10001 2026-07-03] [10002 , 10001 2026-07-03]", again + " " + after + " " + this.numbers(again),
            "The document of a graduate found again was lost, or the number of one gone was not freed"
        );
    }

    @Test
    void cannotForgetOrganization() throws Exception {
        this.documents.organization(new Organization(
            "федеральное государственное\nбюджетное образовательное учреждение", "г. Йошкар-Ола", "Петров", "Пётр",
            "Петрович"
        ));
        final Organization read = new Documents(this.database).organization();
        assertEquals(
            "федеральное государственное\nбюджетное образовательное учреждение|г. Йошкар-Ола|П.П. Петров",
            read.fullName + "|" + read.locality + "|" + read.head(),
            "The organization was not kept as entered, with the lines of its name"
        );
    }
}
