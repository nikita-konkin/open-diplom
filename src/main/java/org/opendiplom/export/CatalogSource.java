package org.opendiplom.export;

import java.util.ArrayList;
import java.util.List;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;

/**
 * Graduates for the XML from the registry. The results are already in the
 * order of the plan and named as the supplement prints them; a facultative
 * without a grade is left out.
 */
public final class CatalogSource {
    private CatalogSource() {
    }

    /**
     * @param stateExam whether the attestation of the plan has a state exam
     */
    public static List<Graduate> graduates(final List<GraduateRecord> records, final boolean stateExam) {
        final List<Graduate> graduates = new ArrayList<>();
        for (final GraduateRecord record : records) {
            final Graduate graduate = new Graduate(
                record.lastName, record.firstName, record.middleName, record.birthDate, record.previousDocument,
                record.previousYear, record.gekDate, record.gekProtocol, record.thesisTopic, record.thesisGrade
            );
            if (stateExam && record.stateExamGrade != null) {
                graduate.stateExams.add(record.stateExamGrade);
            }
            for (final ResultRecord result : record.results) {
                if (result.grade == null) {
                    continue;
                }
                graduate.results.add(new Graduate.Result(
                    result.kind, result.printed, result.grade,
                    result.credits == null ? 0 : (int) Math.round(result.credits)
                ));
            }
            graduates.add(graduate);
        }
        return graduates;
    }
}
