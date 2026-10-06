package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.graduation.Edits;
import org.opendiplom.plans.PlanHeader;

/** Blanks of strict accounting and the handing of documents, in SQLite (ADR-0013). */
final class StockTest {
    private static final Stock.Form DIPLOMA = new Stock.Form(Blanks.DIPLOMA, "03", false);
    private static final Stock.Form SUPPLEMENT = new Stock.Form(Blanks.SUPPLEMENT, "03", false);
    private static final String SERIES = "123456";

    @TempDir
    Path folder;

    private Database database;
    private Graduations graduations;
    private Documents documents;
    private Stock stock;
    private Curricula.Saved plan;

    @BeforeEach
    void open() throws Exception {
        this.database = Database.open("jdbc:sqlite:" + this.folder.resolve("open-diplom.db"));
        this.graduations = new Graduations(this.database);
        this.documents = new Documents(this.database);
        this.stock = new Stock(this.database);
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

    /** A graduation of these surnames registered, the documents numbered from 10001 and dated. */
    private String registered(final String... surnames) throws Exception {
        final String id = this.graduations.stage("ИТС-41", "очная", 2021, "Ведомость.xlsx", "Сведения.xlsx");
        this.graduations.curriculum(id, this.plan.id);
        final List<GraduateRecord> graduates = new ArrayList<>();
        for (final String surname : surnames) {
            graduates.add(graduate(graduates.size(), surname));
        }
        final String graduation = this.graduations.register(
            id, graduates, new Curricula(this.database).find(this.plan.id).programId, Collections.emptyList()
        );
        this.documents.give(graduation, this.graduations.graduates(graduation),
            List.of("10001", "10002", "10003", "10004").subList(0, surnames.length), "2026-07-03");
        return graduation;
    }

    /** The originals of the graduation in the order of the list. */
    private List<DocumentRecord> originals(final String graduation) throws Exception {
        final Map<String, List<DocumentRecord>> all = this.documents.of(graduation);
        final List<DocumentRecord> originals = new ArrayList<>();
        for (final GraduateRecord graduate : this.graduations.graduates(graduation)) {
            originals.add(Documents.original(all, graduate.id));
        }
        return originals;
    }

    private List<String> ids(final String graduation) throws Exception {
        final List<String> ids = new ArrayList<>();
        for (final DocumentRecord original : this.originals(graduation)) {
            ids.add(original.id);
        }
        return ids;
    }

    /** «10001 0000101 used, …» of the blanks of a graduation, in the order of the documents. */
    private String blanks(final String graduation) throws Exception {
        final Map<String, List<Stock.Blank>> blanks = this.stock.of(graduation);
        final List<String> found = new ArrayList<>();
        for (final DocumentRecord original : this.originals(graduation)) {
            for (final Stock.Blank blank : blanks.getOrDefault(original.id, List.of())) {
                found.add(original.regNumber + " " + blank.form.kind + " " + blank.number + " " + blank.status);
            }
        }
        return String.join(", ", found);
    }

    private static Edits.Edit edit(final String field, final String value, final String original) {
        return new Edits.Edit("", field, null, "", value, original, "2026-07-01T00:00:00Z");
    }

    @Test
    void cannotTakeBlankInTwice() throws Exception {
        assertEquals(5, this.stock.receive(DIPLOMA, SERIES, "0000101–0000105", "2026-06-01", "накладная 7"),
            "A batch of five blanks did not come in whole");
        final IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
            () -> this.stock.receive(DIPLOMA, SERIES, "0000105-0000106", "2026-06-02", ""),
            "A blank in the stock already came in again");
        assertTrue(twice.getMessage().contains("0000105"), "The blank taken in twice is not named: " + twice.getMessage());
        assertEquals(5, this.stock.receive(SUPPLEMENT, SERIES, "0000101-0000105", "2026-06-01", ""),
            "The supplements, numbered apart from the diplomas, were refused for the numbers of the diplomas");
        assertThrows(IllegalArgumentException.class,
            () -> this.stock.receive(DIPLOMA, "12345", "0000201", "2026-06-01", ""), "A series of five digits was taken");
        assertThrows(IllegalArgumentException.class,
            () -> this.stock.receive(DIPLOMA, SERIES, "201", "2026-06-01", ""), "A number of three digits was taken");
        final Stock.Count count = this.stock.counts().get(0);
        assertEquals("Диплом бакалавра 5 0 0", count.form.title() + " " + count.free + " " + count.used + " " + count.spoiled,
            "The refused batches changed the count");
    }

    @Test
    void cannotPutDocumentsOnBlanksOutOfOrder() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов", "Васильев");
        this.stock.receive(DIPLOMA, SERIES, "0000101-0000105", "2026-06-01", "");
        this.stock.spoil(DIPLOMA, SERIES, "0000102", "замят при пробной печати");
        assertEquals(3, this.stock.record(graduation, List.of(
            new Stock.Run(DIPLOMA, SERIES, "0000101", this.ids(graduation))
        )), "Not every document of the run got a blank");
        assertEquals(
            "10001 diploma 0000101 used, 10002 diploma 0000103 used, 10003 diploma 0000104 used",
            this.blanks(graduation),
            "The blanks of the run did not go to the documents in the order of both, past the spoiled one"
        );
    }

    @Test
    void cannotRecordRunShortOfBlanks() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов", "Васильев");
        this.stock.receive(DIPLOMA, SERIES, "0000101-0000103", "2026-06-01", "");
        this.stock.receive(SUPPLEMENT, SERIES, "0000101-0000102", "2026-06-01", "");
        final IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> this.stock.record(graduation, List.of(
                new Stock.Run(DIPLOMA, SERIES, "0000101", this.ids(graduation)),
                new Stock.Run(SUPPLEMENT, SERIES, "0000101", this.ids(graduation))
            )), "A run of three documents went on two blanks");
        assertTrue(refused.getMessage().contains("свободно 2, а документов 3"), "The shortage is not told: "
            + refused.getMessage());
        assertEquals("", this.blanks(graduation), "The run with enough blanks was written though the other was refused");
    }

    @Test
    void cannotPrintDocumentOnSecondBlankOfKind() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов");
        this.stock.receive(DIPLOMA, SERIES, "0000101-0000103", "2026-06-01", "");
        final List<String> ids = this.ids(graduation);
        this.stock.record(graduation, ids.get(0), DIPLOMA, SERIES, "0000101");
        assertThrows(IllegalArgumentException.class,
            () -> this.stock.record(graduation, ids.get(0), DIPLOMA, SERIES, "0000102"),
            "A document got a second diploma blank while the first is not spoiled");
        final IllegalArgumentException used = assertThrows(IllegalArgumentException.class,
            () -> this.stock.record(graduation, ids.get(1), DIPLOMA, SERIES, "0000101"),
            "The blank of one document went to another");
        assertTrue(used.getMessage().contains("10001"), "The document on the blank is not named: " + used.getMessage());
        this.stock.spoil(DIPLOMA, SERIES, "0000101", "ошибка в фамилии");
        this.stock.record(graduation, ids.get(0), DIPLOMA, SERIES, "0000102");
        assertEquals("10001 diploma 0000101 spoiled, 10001 diploma 0000102 used", this.blanks(graduation),
            "The document printed again did not keep the spoiled blank beside the new one");
    }

    @Test
    void cannotChangeWhatPrintedBlankHas() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов");
        this.stock.receive(DIPLOMA, SERIES, "0000101-0000103", "2026-06-01", "");
        final DocumentRecord printed = this.originals(graduation).get(0);
        this.stock.record(graduation, printed.id, DIPLOMA, SERIES, "0000101");
        assertThrows(IllegalArgumentException.class, () -> this.documents.save(graduation, new DocumentRecord(
            printed.id, printed.graduateId, null, false, false, "10001", "2026-07-04", null
        ), "дата"), "The date of issue on a printed diploma changed");
        assertThrows(IllegalArgumentException.class, () -> this.documents.save(graduation, new DocumentRecord(
            printed.id, printed.graduateId, null, false, false, "10001", "2026-07-03", true
        ), "с отличием"), "A printed diploma became one «с отличием»");
        final IllegalArgumentException all = assertThrows(IllegalArgumentException.class,
            () -> this.documents.give(graduation, this.graduations.graduates(graduation), List.of(), "2026-07-10"),
            "The date of the whole graduation changed over a printed diploma");
        assertTrue(all.getMessage().contains("Андреев") && !all.getMessage().contains("Борисов"),
            "The graduates the date is printed for are not named: " + all.getMessage());
        this.stock.spoil(DIPLOMA, SERIES, "0000101", "ошибка в дате");
        this.documents.save(graduation, new DocumentRecord(
            printed.id, printed.graduateId, null, false, false, "10001", "2026-07-04", null
        ), "дата");
        assertEquals("10001 2026-07-04", this.originals(graduation).get(0).regNumber + " "
            + this.originals(graduation).get(0).issueDate, "The date did not change once the blank was spoiled");
    }

    @Test
    void cannotCorrectGraduateUnderPrintedBlank() throws Exception {
        final String graduation = this.registered("Андреев");
        this.stock.receive(DIPLOMA, SERIES, "0000101", "2026-06-01", "");
        this.stock.receive(SUPPLEMENT, SERIES, "0000201", "2026-06-01", "");
        final DocumentRecord original = this.originals(graduation).get(0);
        this.stock.record(graduation, original.id, DIPLOMA, SERIES, "0000101");
        this.graduations.edit(graduation, original.graduateId, "Андреев", List.of(edit("thesis_topic", "Сети", "Тема")),
            List.of());
        assertThrows(IllegalArgumentException.class, () -> this.graduations.edit(graduation, original.graduateId,
            "Андреев", List.of(edit("gek_protocol", "7", "3")), List.of()),
            "The protocol of the ГЭК changed under a printed diploma");
        this.stock.record(graduation, original.id, SUPPLEMENT, SERIES, "0000201");
        assertThrows(IllegalArgumentException.class, () -> this.graduations.edit(graduation, original.graduateId,
            "Андреев", List.of(edit("thesis_topic", "Связь", "Тема")), List.of()),
            "The topic of the thesis changed under a printed supplement");
        this.stock.issue(graduation, List.of(original.id), "2026-07-03", Stock.PERSON, "");
        this.graduations.edit(graduation, original.graduateId, "Андреев", List.of(edit("gek_protocol", "7", "3")),
            List.of());
        assertEquals(2, this.graduations.edits(graduation).get(original.graduateId).size(),
            "A correction for a duplicate was refused because the handed original has it otherwise");
    }

    @Test
    void cannotHandDocumentNotOnAllBlanks() throws Exception {
        final String graduation = this.registered("Андреев");
        this.stock.receive(DIPLOMA, SERIES, "0000101", "2026-06-01", "");
        this.stock.receive(SUPPLEMENT, SERIES, "0000201", "2026-06-01", "");
        final DocumentRecord original = this.originals(graduation).get(0);
        this.stock.record(graduation, original.id, DIPLOMA, SERIES, "0000101");
        final IllegalArgumentException unprinted = assertThrows(IllegalArgumentException.class,
            () -> this.stock.issue(graduation, List.of(original.id), "2026-07-03", Stock.PERSON, ""),
            "A document without its supplement was handed");
        assertTrue(unprinted.getMessage().contains("приложение"), "The blank missing is not named: "
            + unprinted.getMessage());
        this.stock.record(graduation, original.id, SUPPLEMENT, SERIES, "0000201");
        assertThrows(IllegalArgumentException.class,
            () -> this.stock.issue(graduation, List.of(original.id), "2026-07-03", Stock.ATTORNEY, ""),
            "A document was handed by a power of attorney without its number and date");
        this.stock.issue(graduation, List.of(original.id), "2026-07-05", Stock.ATTORNEY, "№ 12 от 01.07.2026");
        assertEquals("2026-07-05 по доверенности № 12 от 01.07.2026", this.stock.issues(graduation).get(original.id).handed
            + " " + this.stock.issues(graduation).get(original.id).text(), "The handing was not kept as written");
        assertThrows(IllegalArgumentException.class,
            () -> this.stock.issue(graduation, List.of(original.id), "2026-07-06", Stock.PERSON, ""),
            "A document was handed twice");
        assertThrows(IllegalArgumentException.class, () -> this.stock.spoil(DIPLOMA, SERIES, "0000101", "ошибка"),
            "The blank of a handed document was spoiled");
        final IllegalArgumentException handed = assertThrows(IllegalArgumentException.class,
            () -> this.documents.save(graduation, new DocumentRecord(
                original.id, original.graduateId, null, false, false, "10009", "2026-07-03", null
            ), "номер"), "The number of a handed document changed");
        assertTrue(handed.getMessage().contains("дубликат"), "The way out is not told: " + handed.getMessage());
    }

    @Test
    void cannotReplaceGraduationOnBlanks() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов");
        this.stock.receive(DIPLOMA, SERIES, "0000101", "2026-06-01", "");
        this.stock.record(graduation, this.ids(graduation).get(0), DIPLOMA, SERIES, "0000101");
        final String again = this.graduations.stage("ИТС-41", "очная", 2021, "Ведомость.xlsx", "Сведения.xlsx");
        this.graduations.curriculum(again, this.plan.id);
        assertThrows(IllegalStateException.class, () -> this.graduations.register(
            again, List.of(graduate(0, "Борисов")), new Curricula(this.database).find(this.plan.id).programId,
            Collections.emptyList()
        ), "New files replaced a graduation with a document on a blank");
        assertEquals("Андреев Иван Иванович", this.graduations.graduates(graduation).get(0).fullName(),
            "The refused files changed the graduates");
    }

    @Test
    void cannotLoseSpoiledBlankWhenGraduateLeaves() throws Exception {
        final String graduation = this.registered("Андреев", "Борисов");
        this.stock.receive(DIPLOMA, SERIES, "0000101", "2026-06-01", "");
        this.stock.record(graduation, this.ids(graduation).get(0), DIPLOMA, SERIES, "0000101");
        this.stock.spoil(DIPLOMA, SERIES, "0000101", "отчислен до выдачи");
        final String again = this.graduations.stage("ИТС-41", "очная", 2021, "Ведомость.xlsx", "Сведения.xlsx");
        this.graduations.curriculum(again, this.plan.id);
        this.graduations.register(again, List.of(graduate(0, "Борисов")),
            new Curricula(this.database).find(this.plan.id).programId, Collections.emptyList());
        final List<Stock.Blank> spoiled = this.stock.spoiled();
        assertEquals("1 0000101 отчислен до выдачи true", spoiled.size() + " " + spoiled.get(0).number + " "
            + spoiled.get(0).reason + " " + (spoiled.get(0).documentId == null),
            "The spoiled blank of the graduate gone from the files left the stock, or still points to the document");
    }
}
