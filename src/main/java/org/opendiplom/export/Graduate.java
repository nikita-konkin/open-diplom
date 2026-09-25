package org.opendiplom.export;

import java.util.ArrayList;
import java.util.List;

/** A graduate as the CyberDiploma XML carries them: personal data, ГИА and grades. */
public final class Graduate {
    /** One graded element of the supplement. */
    public static final class Result {
        final String type;
        final String name;
        final int grade;
        final int credits;

        Result(final String type, final String name, final int grade, final int credits) {
            this.type = type;
            this.name = name;
            this.grade = grade;
            this.credits = credits;
        }
    }

    final String lastName;
    final String firstName;
    final String middleName;
    final String birthDate;
    final String previousDocument;
    final String previousYear;
    final String gekDate;
    final String gekProtocol;
    final String thesisTopic;
    final Integer thesisGrade;
    /** State exams listed before the thesis. */
    final List<Integer> stateExams = new ArrayList<>();
    /** Disciplines, practices, course works, electives and extra exams, in pivot order. */
    final List<Result> results = new ArrayList<>();

    Graduate(
        final String lastName, final String firstName, final String middleName,
        final String birthDate, final String previousDocument, final String previousYear,
        final String gekDate, final String gekProtocol, final String thesisTopic,
        final Integer thesisGrade
    ) {
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.birthDate = birthDate;
        this.previousDocument = previousDocument;
        this.previousYear = previousYear;
        this.gekDate = gekDate;
        this.gekProtocol = gekProtocol;
        this.thesisTopic = thesisTopic;
        this.thesisGrade = thesisGrade;
    }
}
