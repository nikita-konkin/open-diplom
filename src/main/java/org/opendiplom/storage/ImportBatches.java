package org.opendiplom.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.opendiplom.imports.CreditCheck;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.sheets.Cells;

/** Imported statements waiting in the staging zone. */
public final class ImportBatches {
    private final Database database;

    public ImportBatches(final Database database) {
        this.database = database;
    }

    /** A saved import, for the list on the main page. */
    public static final class Batch {
        public final String id;
        public final String createdAt;
        public final String statementFile;
        public final String curriculumFile;
        public final int students;
        public final int subjects;

        Batch(final ResultSet row) throws SQLException {
            this.id = row.getString("id");
            this.createdAt = row.getString("created_at");
            this.statementFile = row.getString("statement_file");
            this.curriculumFile = row.getString("curriculum_file");
            this.students = row.getInt("students");
            this.subjects = row.getInt("subjects");
        }
    }

    /** Saves an import in one transaction and returns its identifier. */
    public String save(
        final String statementFile, final String curriculumFile,
        final StatementImport statement, final List<CreditCheck> checks
    ) throws SQLException {
        final String id = UUID.randomUUID().toString();
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement batch = connection.prepareStatement(
                "INSERT INTO import_batch (id, created_at, statement_file, curriculum_file, students, subjects) "
                    + "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                batch.setString(1, id);
                batch.setString(2, Instant.now().toString());
                batch.setString(3, statementFile);
                batch.setString(4, curriculumFile);
                batch.setInt(5, statement.students().size());
                batch.setInt(6, statement.labels().size());
                batch.executeUpdate();
            }
            try (PreparedStatement result = connection.prepareStatement(
                "INSERT INTO import_result (batch_id, student, label, grade) VALUES (?, ?, ?, ?)"
            )) {
                for (final String student : statement.students()) {
                    for (final String label : statement.labels()) {
                        final Object grade = statement.grade(student, label);
                        result.setString(1, id);
                        result.setString(2, student);
                        result.setString(3, label);
                        result.setString(4, grade == null ? null : Cells.text(grade));
                        result.addBatch();
                    }
                }
                result.executeBatch();
            }
            try (PreparedStatement check = connection.prepareStatement(
                "INSERT INTO import_check (batch_id, label, settled, source, notes) VALUES (?, ?, ?, ?, ?)"
            )) {
                for (final CreditCheck item : checks) {
                    check.setString(1, id);
                    check.setString(2, item.label());
                    check.setString(3, item.settled());
                    check.setString(4, item.source());
                    check.setString(5, item.notes());
                    check.addBatch();
                }
                check.executeBatch();
            }
            connection.commit();
        }
        return id;
    }

    /** Latest imports first. */
    public List<Batch> latest(final int limit) throws SQLException {
        final List<Batch> batches = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM import_batch ORDER BY created_at DESC LIMIT ?"
             )) {
            query.setInt(1, limit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    batches.add(new Batch(rows));
                }
            }
        }
        return batches;
    }

    /** Grade of a student saved with an import, {@code null} when there is none. */
    public String grade(final String batch, final String student, final String label) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT grade FROM import_result WHERE batch_id = ? AND student = ? AND label = ?"
             )) {
            query.setString(1, batch);
            query.setString(2, student);
            query.setString(3, label);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
