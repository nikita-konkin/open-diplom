package org.opendiplom.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Organization;

/**
 * Documents of the graduates and the organization that issues them
 * (ADR-0010). A registration number is used once in the whole registry.
 */
public final class Documents {
    static final String NUMBERS = "numbers";
    static final String DOCUMENT = "document";
    static final String DUPLICATE = "duplicate";
    private static final String MAIN = "main";

    private final Database database;

    public Documents(final Database database) {
        this.database = database;
    }

    /** What a giving of numbers did. */
    public static final class Given {
        /** Graduates who got a number. */
        public final int numbered;
        /** Numbers left over, in the order written. */
        public final List<String> left;
        /** Graduates still without a number for want of numbers. */
        public final int wanting;

        Given(final int numbered, final List<String> left, final int wanting) {
            this.numbered = numbered;
            this.left = left;
            this.wanting = wanting;
        }
    }

    /** The organization, empty fields when not filled in yet. */
    public Organization organization() throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement("SELECT * FROM organization WHERE id = ?")) {
            query.setString(1, MAIN);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return Organization.empty();
                }
                return new Organization(
                    row.getString("full_name"), row.getString("locality"), row.getString("head_last_name"),
                    row.getString("head_first_name"), row.getString("head_middle_name")
                );
            }
        }
    }

    public void organization(final Organization organization) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            delete(connection, "DELETE FROM organization WHERE id = ?", MAIN);
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO organization (id, full_name, locality, head_last_name, head_first_name, head_middle_name, "
                    + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )) {
                insert.setString(1, MAIN);
                insert.setString(2, organization.fullName);
                insert.setString(3, organization.locality);
                insert.setString(4, organization.headLastName);
                insert.setString(5, organization.headFirstName);
                insert.setString(6, organization.headMiddleName);
                insert.setString(7, Instant.now().toString());
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO audit (id, at, subject, subject_id, action, details) VALUES (?, ?, 'organization', ?, ?, ?)"
            )) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, Instant.now().toString());
                insert.setString(3, MAIN);
                insert.setString(4, "saved");
                insert.setString(5, organization.fullName.replace('\n', ' ') + ", " + organization.locality + ", "
                    + organization.head());
                insert.executeUpdate();
            }
            connection.commit();
        }
    }

    /** Documents of the graduates of a graduation by graduate: the original first, then the duplicates. */
    public Map<String, List<DocumentRecord>> of(final String graduationId) throws SQLException {
        final Map<String, List<DocumentRecord>> documents = new LinkedHashMap<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT d.* FROM document d JOIN graduate g ON g.id = d.graduate_id WHERE g.graduation_id = ? "
                     + "ORDER BY CASE WHEN d.duplicate_of IS NULL THEN 0 ELSE 1 END, d.created_at"
             )) {
            query.setString(1, graduationId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    final DocumentRecord document = record(row);
                    documents.computeIfAbsent(document.graduateId, any -> new ArrayList<>()).add(document);
                }
            }
        }
        return documents;
    }

    /** The original document of a graduate, a blank one when there is none yet. */
    public static DocumentRecord original(final Map<String, List<DocumentRecord>> documents, final String graduateId) {
        for (final DocumentRecord document : documents.getOrDefault(graduateId, new ArrayList<>())) {
            if (!document.duplicate()) {
                return document;
            }
        }
        return DocumentRecord.blank(graduateId);
    }

    /**
     * Gives the numbers, in the order written, to the graduates without one,
     * in the order of the graduation, and sets the date of issue of all its
     * originals when one is given.
     *
     * @throws IllegalArgumentException when a number is in the registry already
     */
    public Given give(
        final String graduationId, final List<GraduateRecord> graduates, final List<String> numbers,
        final String issueDate
    ) throws SQLException {
        final Map<String, List<DocumentRecord>> documents = this.of(graduationId);
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final List<String> taken = taken(connection, numbers, null);
            if (!taken.isEmpty()) {
                throw new IllegalArgumentException("Номера уже есть в картотеке: " + String.join(", ", taken));
            }
            int next = 0;
            int wanting = 0;
            final List<String> given = new ArrayList<>();
            for (final GraduateRecord graduate : graduates) {
                final DocumentRecord original = original(documents, graduate.id);
                String number = original.regNumber;
                if (number.isEmpty() && !numbers.isEmpty()) {
                    if (next < numbers.size()) {
                        number = numbers.get(next++);
                        given.add(number);
                    } else {
                        ++wanting;
                    }
                }
                final String date = issueDate.isEmpty() ? original.issueDate : issueDate;
                if (!number.equals(original.regNumber) || !date.equals(original.issueDate)) {
                    save(connection, new DocumentRecord(
                        original.id, graduate.id, null, false, false, number, date, original.honors
                    ));
                }
            }
            Graduations.audit(connection, graduationId, NUMBERS, (given.isEmpty() ? "" : "номера: "
                + String.join(", ", given)) + (issueDate.isEmpty() ? "" : (given.isEmpty() ? "" : "; ")
                + "дата выдачи " + issueDate));
            connection.commit();
            return new Given(given.size(), new ArrayList<>(numbers.subList(next, numbers.size())), wanting);
        }
    }

    /**
     * Saves a document of a graduate of the graduation.
     *
     * @throws IllegalArgumentException when its number belongs to another document
     */
    public void save(final String graduationId, final DocumentRecord document, final String what) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            if (!document.regNumber.isEmpty()) {
                final List<String> taken = taken(connection, List.of(document.regNumber), document.id);
                if (!taken.isEmpty()) {
                    throw new IllegalArgumentException("Номер " + document.regNumber + " уже есть в картотеке");
                }
            }
            save(connection, document);
            Graduations.audit(connection, graduationId, document.duplicate() ? DUPLICATE : DOCUMENT, what);
            connection.commit();
        }
    }

    /** Deletes the documents of a graduate leaving the registry; the numbers they had. */
    static List<String> forget(final Connection connection, final String graduateId) throws SQLException {
        final List<String> numbers = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT reg_number FROM document WHERE graduate_id = ? AND reg_number IS NOT NULL"
        )) {
            query.setString(1, graduateId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    numbers.add(row.getString(1));
                }
            }
        }
        delete(connection, "DELETE FROM document WHERE graduate_id = ? AND duplicate_of IS NOT NULL", graduateId);
        delete(connection, "DELETE FROM document WHERE graduate_id = ?", graduateId);
        return numbers;
    }

    /** Of the numbers, those another document has. */
    private static List<String> taken(final Connection connection, final List<String> numbers, final String except)
        throws SQLException {
        final List<String> taken = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT id FROM document WHERE reg_number = ?"
        )) {
            for (final String number : numbers) {
                query.setString(1, number);
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        if (!row.getString(1).equals(except)) {
                            taken.add(number);
                        }
                    }
                }
            }
        }
        return taken;
    }

    private static void save(final Connection connection, final DocumentRecord document) throws SQLException {
        final String now = Instant.now().toString();
        final boolean fresh = document.id == null;
        try (PreparedStatement statement = connection.prepareStatement(fresh
            ? "INSERT INTO document (graduate_id, duplicate_of, diploma_duplicate, supplement_duplicate, reg_number, "
                + "issue_date, honors, updated_at, created_at, id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            : "UPDATE document SET graduate_id = ?, duplicate_of = ?, diploma_duplicate = ?, supplement_duplicate = ?, "
                + "reg_number = ?, issue_date = ?, honors = ?, updated_at = ? WHERE id = ?"
        )) {
            int column = 1;
            statement.setString(column++, document.graduateId);
            statement.setString(column++, document.duplicateOf);
            statement.setInt(column++, document.diplomaDuplicate ? 1 : 0);
            statement.setInt(column++, document.supplementDuplicate ? 1 : 0);
            if (document.regNumber.isEmpty()) {
                statement.setNull(column++, Types.VARCHAR);
            } else {
                statement.setString(column++, document.regNumber);
            }
            statement.setString(column++, document.issueDate);
            if (document.honors == null) {
                statement.setNull(column++, Types.INTEGER);
            } else {
                statement.setInt(column++, document.honors ? 1 : 0);
            }
            statement.setString(column++, now);
            if (fresh) {
                statement.setString(column++, now);
                statement.setString(column, UUID.randomUUID().toString());
            } else {
                statement.setString(column, document.id);
            }
            statement.executeUpdate();
        }
    }

    private static DocumentRecord record(final ResultSet row) throws SQLException {
        final int honors = row.getInt("honors");
        final Boolean decided = row.wasNull() ? null : honors == 1;
        return new DocumentRecord(
            row.getString("id"), row.getString("graduate_id"), row.getString("duplicate_of"),
            row.getInt("diploma_duplicate") == 1, row.getInt("supplement_duplicate") == 1, row.getString("reg_number"),
            row.getString("issue_date"), decided
        );
    }

    private static void delete(final Connection connection, final String sql, final String id) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql)) {
            delete.setString(1, id);
            delete.executeUpdate();
        }
    }
}
