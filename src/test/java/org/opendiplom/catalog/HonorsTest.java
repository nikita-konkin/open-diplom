package org.opendiplom.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** «С отличием» by item 27 of Order No 670, every grade counted (the owner, 02.10.2026). */
final class HonorsTest {
    /** A graduate with these grades of disciplines, the thesis and the state exam. */
    private static GraduateRecord graduate(final Integer thesis, final Integer exam, final Integer... grades) {
        final List<ResultRecord> results = new ArrayList<>();
        for (final Integer grade : grades) {
            results.add(new ResultRecord(
                results.size(), ResultRecord.DISCIPLINE, "Дисциплина " + results.size(), grade,
                grade == null ? "" : String.valueOf(grade), 3.0
            ));
        }
        return new GraduateRecord(
            "g", 0, "Иванов", "Иван", "Иванович", "2001-02-03", "Аттестат", "2019", "2026-06-24", "3", "Тема", thesis,
            exam, "Иванов И. И.", "1", "", results
        );
    }

    @Test
    void cannotRefuseHonorsAtExactlyThreeQuarters() {
        // «отлично» 6 of 8 with the attestation, the same share as 12 of 16
        final Honors honors = Honors.of(graduate(5, 5, 5, 5, 5, 5, 4, 4, 6, 6), true);
        assertEquals(
            "true 6 8", honors.proposal + " " + honors.excellent + " " + honors.counted,
            "Three quarters of «отлично» without «зачтено» did not make honors: " + honors.explanation
        );
    }

    @Test
    void cannotGiveHonorsWithAttestationBelowExcellent() {
        assertEquals(
            Boolean.FALSE, Honors.of(graduate(5, 4, 5, 5, 5, 5, 5, 5), true).proposal,
            "Honors were proposed with the state exam «хорошо»"
        );
    }

    @Test
    void cannotGiveHonorsWithSatisfactoryGrade() {
        assertEquals(
            Boolean.FALSE, Honors.of(graduate(5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 3), true).proposal,
            "Honors were proposed with a «удовлетворительно» among «отлично»"
        );
    }

    @Test
    void cannotForgetFacultativeGrade() {
        final GraduateRecord graduate = graduate(5, 5, 5, 5, 4);
        final List<ResultRecord> results = new ArrayList<>(graduate.results);
        results.add(new ResultRecord(9, ResultRecord.FACULTATIVE, "Теория игр", 4, "4", 2.0));
        final GraduateRecord with = new GraduateRecord(
            "g", 0, "Иванов", "Иван", "Иванович", "2001-02-03", "Аттестат", "2019", "2026-06-24", "3", "Тема", 5, 5,
            "Иванов И. И.", "1", "", results
        );
        assertEquals(
            "true false", Honors.of(graduate, true).proposal + " " + Honors.of(with, true).proposal,
            "The grade of a facultative did not count, though the university counts every grade"
        );
    }

    @Test
    void cannotDecideWhileGradeIsPlaceholder() {
        final Honors honors = Honors.of(graduate(5, 5, 5, 5, 7), true);
        assertNull(honors.proposal, "Honors were decided with code 7 in place of a grade: " + honors.explanation);
    }

    @Test
    void cannotAskStateExamThePlanHasNot() {
        assertEquals(
            Boolean.TRUE, Honors.of(graduate(5, null, 5, 5, 5, 4), false).proposal,
            "A plan without a state exam still waited for its grade"
        );
    }

    @Test
    void cannotMiscountAverage() {
        assertEquals(
            "«отлично» 3 из 4 (75 %), средний балл 4,75; ниже «хорошо» нет; ГИА «отлично»",
            Honors.of(graduate(5, null, 5, 5, 4, 6), false).explanation,
            "The reckoning shown to the operator is wrong"
        );
    }

    @Test
    void cannotCountPassedAsGrade() {
        assertEquals(
            Arrays.asList(1, 1), Arrays.asList(Honors.of(graduate(5, null, 6, 6, 6), false).counted,
                Honors.of(graduate(5, null), false).counted),
            "«зачтено» was counted among the grades"
        );
    }
}
