package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Plans;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;

/** Editions of curricula in SQLite. */
final class CurriculaTest {
    @TempDir
    Path folder;

    private Curricula curricula() throws Exception {
        return new Curricula(Database.open("jdbc:sqlite:" + this.folder.resolve("open-diplom.db")));
    }

    private static PlanHeader header(final String form, final String year) {
        return new PlanHeader(
            "11.03.02", "Инфокоммуникационные технологии и системы связи",
            "\"Интеллектуальные инфокоммуникационные системы\"", "бакалавр", form, "4 года", year
        );
    }

    private static List<PlanRow> changed() {
        return Plans.with(
            Plans.bachelor(), Plans.PRACTICE, Plans.row("Б.2.1.1.1", Plans.PRACTICE, ";;8;;", "9 - 9", "")
        );
    }

    @Test
    void cannotSaveSamePlanTwice() throws Exception {
        final Curricula.Saved first = this.curricula().save(header("Очная", "2021"), Plans.bachelor(), "pdf", "План.pdf", null, "");
        final Curricula.Saved again = this.curricula().save(header("очная", "2021"), Plans.bachelor(), "xlsx", "План.xlsx", null, "");
        assertEquals(
            first.id + " " + first.edition + " true", again.id + " " + again.edition + " " + again.unchanged,
            "The same plan loaded again became a new edition"
        );
    }

    @Test
    void cannotOverwriteEditionWhenPlanChanges() throws Exception {
        final Curricula.Saved first = this.curricula().save(header("очная", "2021"), Plans.bachelor(), "pdf", null, null, "");
        final Curricula.Saved second = this.curricula().save(header("очная", "2021"), changed(), "manual", null, first.id, "");
        assertEquals(2, second.edition, "A changed plan did not become the next edition");
        assertEquals(
            Curricula.fingerprint(header("очная", "2021"), Plans.bachelor()),
            Curricula.fingerprint(header("очная", "2021"), this.curricula().rows(first.id)),
            "Saving a new edition changed the rows of the earlier one"
        );
    }

    @Test
    void cannotLoseRowsAfterReopening() throws Exception {
        final Curricula.Saved saved = this.curricula().save(header("очная", "2021"), Plans.bachelor(), "pdf", null, null, "");
        final Curricula reopened = this.curricula();
        assertEquals(
            Curricula.fingerprint(header("очная", "2021"), Plans.bachelor()),
            Curricula.fingerprint(reopened.find(saved.id).header, reopened.rows(saved.id)),
            "The title or a cell of a row changed on the way to SQLite and back"
        );
    }

    @Test
    void cannotCompareWithPlanOfOtherFormWhenEarlierYearIsThere() throws Exception {
        final Curricula curricula = this.curricula();
        final String earlier = curricula.save(header("заочная", "2020"), Plans.bachelor(), "pdf", null, null, "").id;
        curricula.save(header("очная", "2021"), Plans.bachelor(), "pdf", null, null, "");
        assertEquals(
            earlier, curricula.nearest(header("заочная", "2021")).id,
            "A plan was compared with another form though its own form had an earlier year"
        );
    }

    @Test
    void cannotLeavePlanWithoutComparisonWhenOtherFormHasSameYear() throws Exception {
        final Curricula curricula = this.curricula();
        final String full = curricula.save(header("очная", "2021"), Plans.bachelor(), "pdf", null, null, "").id;
        assertEquals(
            full, curricula.nearest(header("заочная", "2021")).id,
            "The full-time plan of the same year was not offered for a part-time plan"
        );
        assertNull(curricula.nearest(header("очная", "2021")), "A plan was compared with itself");
    }

    @Test
    void cannotSaveEditionWithoutHistory() throws Exception {
        final Curricula curricula = this.curricula();
        final String full = curricula.save(header("очная", "2021"), Plans.bachelor(), "pdf", "План.pdf", null, "").id;
        final String part = curricula.save(header("заочная", "2021"), changed(), "manual", null, full, "по скану").id;
        final List<String> history = curricula.history(part);
        assertTrue(
            history.size() == 1
                && history.get(0).endsWith("создан на основе другого плана — вручную, редакция 1: по скану"),
            "A plan made from another left no record of how: " + history
        );
    }
}
