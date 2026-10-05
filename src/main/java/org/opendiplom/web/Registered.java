package org.opendiplom.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendiplom.catalog.Checks;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Honors;
import org.opendiplom.catalog.Organization;
import org.opendiplom.export.Program;
import org.opendiplom.export.ProgramFields;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;
import org.opendiplom.storage.Curricula;
import org.opendiplom.storage.Database;
import org.opendiplom.storage.Documents;
import org.opendiplom.storage.Graduations;

/** What the pages of a registered graduation show: the program, the checks and the documents. */
final class Registered {
    final Graduations.Graduation graduation;
    final Curricula.Edition edition;
    final PlanStructure plan;
    final List<GraduateRecord> graduates;
    final List<List<Checks.Finding>> findings = new ArrayList<>();
    /** The original document, the rule of «с отличием» and the findings for printing, by graduate. */
    final List<DocumentRecord> originals = new ArrayList<>();
    final List<Honors> honors = new ArrayList<>();
    final List<List<Checks.Finding>> printing = new ArrayList<>();
    Map<String, List<DocumentRecord>> documents = Collections.emptyMap();
    Organization organization = Organization.empty();
    final Map<String, String> sources = new HashMap<>();
    final Map<String, String> fields;
    final List<String> programProblems = new ArrayList<>();
    final Program program;

    Registered(
        final Graduations.Graduation graduation, final Curricula.Edition edition, final PlanStructure plan,
        final PlanTotals totals, final List<GraduateRecord> graduates
    ) {
        this.graduation = graduation;
        this.edition = edition;
        this.plan = plan;
        this.graduates = graduates;
        if (edition == null) {
            this.fields = Collections.emptyMap();
            this.programProblems.add("у выпуска нет учебного плана");
            this.program = null;
            return;
        }
        final PlanHeader header = edition.header;
        this.fields = ProgramFields.of(
            header.code(), header.direction(), header.profile(), header.qualification(), header.studyForm(),
            header.studyTerm(), totals, this.sources
        );
        if (!graduation.gekChairman.isEmpty()) {
            this.fields.put("gek_chairman", graduation.gekChairman);
            this.sources.put("gek_chairman", "введён на этой странице");
        }
        for (final String[] field : XmlPage.FIELDS) {
            if (!this.fields.containsKey(field[0])) {
                this.programProblems.add("«" + field[1] + "»: " + this.sources.get(field[0]));
            }
        }
        Program built = null;
        try {
            built = ProgramFields.program(this.fields);
        } catch (final ValidationProblems error) {
            this.programProblems.addAll(error.problems());
        }
        this.program = this.programProblems.isEmpty() ? built : null;
        for (final GraduateRecord record : graduates) {
            this.findings.add(Checks.of(record, plan, totals));
        }
    }

    /** A graduation with its plan, graduates and documents; {@code null} when there is none. */
    static Registered load(final Database database, final String id) throws SQLException {
        final Graduations graduations = new Graduations(database);
        final Graduations.Graduation graduation = graduations.find(id);
        if (graduation == null) {
            return null;
        }
        final Curricula curricula = new Curricula(database);
        final Curricula.Edition edition = graduation.curriculumId == null ? null : curricula.find(graduation.curriculumId);
        if (edition == null) {
            return new Registered(graduation, null, null, null, Collections.emptyList());
        }
        final List<PlanRow> rows = curricula.rows(edition.id);
        final Registered registered = new Registered(
            graduation, edition, PlanStructure.of(rows), PlanTotals.of(rows), graduations.graduates(id)
        );
        final Documents documents = new Documents(database);
        registered.document(documents.of(id), documents.organization());
        return registered;
    }

    /** Adds the documents and what printing them asks. */
    void document(final Map<String, List<DocumentRecord>> all, final Organization issuer) {
        this.documents = all;
        this.organization = issuer;
        final boolean exam = Checks.stateExam(this.plan);
        for (int number = 0; number < this.graduates.size(); ++number) {
            final GraduateRecord record = this.graduates.get(number);
            final DocumentRecord original = Documents.original(all, record.id);
            final Honors rule = Honors.of(record, exam);
            this.originals.add(original);
            this.honors.add(rule);
            this.printing.add(Checks.printing(this.findings.get(number), record, original, rule, issuer));
        }
    }

    /** The place of a graduate in the list, -1 when not there. */
    int position(final String graduate) {
        for (int number = 0; number < this.graduates.size(); ++number) {
            if (this.graduates.get(number).id.equals(graduate)) {
                return number;
            }
        }
        return -1;
    }
}
