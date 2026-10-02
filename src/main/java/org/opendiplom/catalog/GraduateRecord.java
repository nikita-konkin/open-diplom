package org.opendiplom.catalog;

import java.util.Collections;
import java.util.List;

/** A graduate in the registry: the information file row, the statement sheet and the results. */
public final class GraduateRecord {
    public final String id;
    /** Place in the information file, from 0. */
    public final int position;
    public final String lastName;
    public final String firstName;
    public final String middleName;
    /** ГГГГ-ММ-ДД, empty when the file had none that could be read. */
    public final String birthDate;
    public final String previousDocument;
    public final String previousYear;
    /** ГГГГ-ММ-ДД, empty when the file had none that could be read. */
    public final String gekDate;
    public final String gekProtocol;
    public final String thesisTopic;
    public final Integer thesisGrade;
    public final Integer stateExamGrade;
    /** «Фамилия И. О.» of the statement sheet. */
    public final String statementName;
    public final String studentNumber;
    /** Problems found reading the information file, one per line. */
    public final String notes;
    /** In the order of the plan. */
    public final List<ResultRecord> results;

    public GraduateRecord(
        final String id, final int position, final String lastName, final String firstName, final String middleName,
        final String birthDate, final String previousDocument, final String previousYear, final String gekDate,
        final String gekProtocol, final String thesisTopic, final Integer thesisGrade, final Integer stateExamGrade,
        final String statementName, final String studentNumber, final String notes, final List<ResultRecord> results
    ) {
        this.id = id;
        this.position = position;
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
        this.stateExamGrade = stateExamGrade;
        this.statementName = statementName;
        this.studentNumber = studentNumber;
        this.notes = notes;
        this.results = Collections.unmodifiableList(results);
    }

    /** «Фамилия Имя Отчество». */
    public String fullName() {
        return (this.lastName + " " + this.firstName + " " + this.middleName).strip();
    }
}
