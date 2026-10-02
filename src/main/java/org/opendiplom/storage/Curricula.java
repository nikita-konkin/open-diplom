package org.opendiplom.storage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opendiplom.plans.PlanCheck;
import org.opendiplom.plans.PlanHeader;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;

/**
 * Curricula by editions (ADR-0008). Saving a plan whose program, form and
 * year are known adds an edition when the plan differs from the latest one
 * and does nothing when it does not.
 */
public final class Curricula {
    private static final String SELECT = "SELECT c.*, p.code, p.direction, p.profile, p.qualification "
        + "FROM curriculum c JOIN program p ON p.id = c.program_id ";
    private static final List<String> CONTROLS =
        Arrays.asList("exams", "tests", "graded_tests", "course_projects", "course_works");
    private static final List<String> CREDITS = Arrays.asList("credits", "credits_exams", "credits_classes");
    private static final List<String> HOURS =
        Arrays.asList("hours", "hours_exams", "hours_classes", "hours_contact", "hours_self");

    private static final Map<String, String> ACTIONS = Map.of(
        "loaded", "загружен из файла", "typed", "введён", "edited", "исправлен",
        "derived", "создан на основе другого плана"
    );

    private final Database database;

    public Curricula(final Database database) {
        this.database = database;
    }

    /** An edition of a curriculum, without its rows. */
    public static final class Edition {
        public final String id;
        public final String programId;
        public final PlanHeader header;
        public final int edition;
        public final String source;
        public final String sourceFile;
        public final String basedOn;
        public final int errors;
        public final String note;
        public final String createdAt;

        Edition(final ResultSet row) throws SQLException {
            this.id = row.getString("id");
            this.programId = row.getString("program_id");
            this.header = new PlanHeader(
                row.getString("code"), row.getString("direction"), row.getString("profile"),
                row.getString("qualification"), row.getString("study_form"), row.getString("study_term"),
                String.valueOf(row.getInt("admission_year"))
            );
            this.edition = row.getInt("edition");
            this.source = row.getString("source");
            this.sourceFile = row.getString("source_file");
            this.basedOn = row.getString("based_on");
            this.errors = row.getInt("errors");
            this.note = row.getString("note");
            this.createdAt = row.getString("created_at");
        }
    }

    /** What saving did: a new edition, or the latest one when the plan is the same. */
    public static final class Saved {
        public final String id;
        public final int edition;
        public final boolean unchanged;

        Saved(final String id, final int edition, final boolean unchanged) {
            this.id = id;
            this.edition = edition;
            this.unchanged = unchanged;
        }
    }

    /**
     * Saves a plan as the next edition of its curriculum.
     *
     * @param header a header whose {@link PlanHeader#missing()} is empty
     * @param source «pdf», «xlsx» or «manual»
     * @param basedOn the edition this one was made from, {@code null} for a file
     */
    public Saved save(
        final PlanHeader header, final List<PlanRow> rows, final String source, final String sourceFile,
        final String basedOn, final String note
    ) throws SQLException {
        if (!header.missing().isEmpty()) {
            throw new IllegalArgumentException("Не заполнено: " + String.join(", ", header.missing()));
        }
        final String fingerprint = fingerprint(header, rows);
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final String program = program(connection, header);
            int edition = 0;
            try (PreparedStatement query = connection.prepareStatement(
                "SELECT id, edition, fingerprint FROM curriculum WHERE program_id = ? AND study_form = ? "
                    + "AND admission_year = ? ORDER BY edition DESC LIMIT 1"
            )) {
                query.setString(1, program);
                query.setString(2, header.studyForm());
                query.setInt(3, header.admissionYear());
                try (ResultSet found = query.executeQuery()) {
                    if (found.next()) {
                        if (found.getString("fingerprint").equals(fingerprint)) {
                            connection.rollback();
                            return new Saved(found.getString("id"), found.getInt("edition"), true);
                        }
                        edition = found.getInt("edition");
                    }
                }
            }
            final String id = UUID.randomUUID().toString();
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO curriculum (id, program_id, study_form, admission_year, edition, study_term, source, "
                    + "source_file, based_on, errors, fingerprint, note, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                insert.setString(1, id);
                insert.setString(2, program);
                insert.setString(3, header.studyForm());
                insert.setInt(4, header.admissionYear());
                insert.setInt(5, edition + 1);
                insert.setString(6, header.studyTerm());
                insert.setString(7, source);
                insert.setString(8, sourceFile);
                insert.setString(9, basedOn);
                insert.setLong(10, PlanCheck.of(PlanStructure.of(rows)).errors());
                insert.setString(11, fingerprint);
                insert.setString(12, note == null ? "" : note);
                insert.setString(13, Instant.now().toString());
                insert.executeUpdate();
            }
            this.items(connection, id, rows);
            // a first edition made from another plan is that plan for another year or form
            final String action = basedOn == null ? "manual".equals(source) ? "typed" : "loaded"
                : edition == 0 ? "derived" : "edited";
            final String from = "pdf".equals(source) ? "PDF" : "xlsx".equals(source) ? "Excel" : "вручную";
            audit(connection, id, action, from + (sourceFile == null ? "" : ", " + sourceFile) + ", редакция "
                + (edition + 1) + (note == null || note.isEmpty() ? "" : ": " + note));
            connection.commit();
            return new Saved(id, edition + 1, false);
        }
    }

    /** An edition, or {@code null} when there is none with the identifier. */
    public Edition find(final String id) throws SQLException {
        final List<Edition> found = this.query(SELECT + "WHERE c.id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Rows of an edition in the order of the plan. */
    public List<PlanRow> rows(final String id) throws SQLException {
        final List<PlanRow> rows = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM curriculum_item WHERE curriculum_id = ? ORDER BY position"
             )) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    final List<String> controls = new ArrayList<>();
                    for (final String column : CONTROLS) {
                        controls.add(row.getString(column));
                    }
                    rows.add(new PlanRow(
                        row.getString("item_index"), row.getString("name"), controls, numbers(row, CREDITS),
                        numbers(row, HOURS)
                    ));
                }
            }
        }
        return rows;
    }

    /** All editions: by direction, profile, form and year, the latest edition first. */
    public List<Edition> all() throws SQLException {
        return this.query(
            SELECT + "ORDER BY p.code, p.profile, c.study_form, c.admission_year DESC, c.edition DESC"
        );
    }

    /** Editions of all programs for a study form and an admission year, the latest edition first. */
    public List<Edition> of(final String studyForm, final int year) throws SQLException {
        return this.query(
            SELECT + "WHERE c.study_form = ? AND c.admission_year = ? ORDER BY p.code, p.profile, c.edition DESC",
            studyForm, year
        );
    }

    /** The latest edition of a curriculum, or {@code null}. */
    public Edition latest(final PlanHeader header) throws SQLException {
        final List<Edition> found = this.query(
            SELECT + "WHERE p.code = ? AND p.profile_key = ? AND c.study_form = ? AND c.admission_year = ? "
                + "ORDER BY c.edition DESC LIMIT 1",
            header.code(), header.profileKey(), header.studyForm(), header.admissionYear()
        );
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * The plan to compare a curriculum with: of the same program, the latest
     * edition of the same form for the nearest earlier year, otherwise of
     * another form for the same year; {@code null} when there is neither.
     */
    public Edition nearest(final PlanHeader header) throws SQLException {
        List<Edition> found = this.query(
            SELECT + "WHERE p.code = ? AND p.profile_key = ? AND c.study_form = ? AND c.admission_year < ? "
                + "ORDER BY c.admission_year DESC, c.edition DESC LIMIT 1",
            header.code(), header.profileKey(), header.studyForm(), header.admissionYear()
        );
        if (found.isEmpty()) {
            found = this.query(
                SELECT + "WHERE p.code = ? AND p.profile_key = ? AND c.study_form <> ? AND c.admission_year = ? "
                    + "ORDER BY c.study_form, c.edition DESC LIMIT 1",
                header.code(), header.profileKey(), header.studyForm(), header.admissionYear()
            );
        }
        return found.isEmpty() ? null : found.get(0);
    }

    /** What was done to an edition, oldest first: «время — действие — подробности». */
    public List<String> history(final String id) throws SQLException {
        final List<String> history = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT at, action, details FROM audit WHERE subject = 'curriculum' AND subject_id = ? ORDER BY at"
             )) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    final String action = ACTIONS.getOrDefault(row.getString("action"), row.getString("action"));
                    history.add(row.getString("at") + " — " + action + " — " + row.getString("details"));
                }
            }
        }
        return history;
    }

    private List<Edition> query(final String sql, final Object... parameters) throws SQLException {
        final List<Edition> editions = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            for (int position = 0; position < parameters.length; ++position) {
                query.setObject(position + 1, parameters[position]);
            }
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    editions.add(new Edition(row));
                }
            }
        }
        return editions;
    }

    /** The program of a header, added when it is new. */
    private static String program(final Connection connection, final PlanHeader header) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT id FROM program WHERE code = ? AND profile_key = ?"
        )) {
            query.setString(1, header.code());
            query.setString(2, header.profileKey());
            try (ResultSet found = query.executeQuery()) {
                if (found.next()) {
                    return found.getString(1);
                }
            }
        }
        final String id = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO program (id, code, profile_key, direction, profile, qualification, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setString(1, id);
            insert.setString(2, header.code());
            insert.setString(3, header.profileKey());
            insert.setString(4, header.direction());
            insert.setString(5, header.profile());
            insert.setString(6, header.qualification());
            insert.setString(7, Instant.now().toString());
            insert.executeUpdate();
        }
        return id;
    }

    private void items(final Connection connection, final String id, final List<PlanRow> rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO curriculum_item (curriculum_id, position, item_index, name, exams, tests, graded_tests, "
                + "course_projects, course_works, credits, credits_exams, credits_classes, hours, hours_exams, "
                + "hours_classes, hours_contact, hours_self) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (int position = 0; position < rows.size(); ++position) {
                final PlanRow row = rows.get(position);
                int column = 1;
                insert.setString(column++, id);
                insert.setInt(column++, position);
                insert.setString(column++, row.index());
                insert.setString(column++, row.name());
                for (final String control : row.controls()) {
                    insert.setString(column++, control);
                }
                for (final Double value : row.creditColumns()) {
                    number(insert, column++, value);
                }
                for (final Double value : row.hours()) {
                    number(insert, column++, value);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void audit(
        final Connection connection, final String id, final String action, final String details
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO audit (id, at, subject, subject_id, action, details) VALUES (?, ?, 'curriculum', ?, ?, ?)"
        )) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, Instant.now().toString());
            insert.setString(3, id);
            insert.setString(4, action);
            insert.setString(5, details.length() > 4000 ? details.substring(0, 4000) : details);
            insert.executeUpdate();
        }
    }

    private static void number(final PreparedStatement insert, final int column, final Double value)
        throws SQLException {
        if (value == null) {
            insert.setNull(column, Types.NUMERIC);
        } else {
            insert.setBigDecimal(column, BigDecimal.valueOf(value));
        }
    }

    private static List<Double> numbers(final ResultSet row, final List<String> columns) throws SQLException {
        final List<Double> numbers = new ArrayList<>();
        for (final String column : columns) {
            final double value = row.getDouble(column);
            numbers.add(row.wasNull() ? null : value);
        }
        return numbers;
    }

    /** SHA-256 of what makes two plans the same: the title and every cell of every row. */
    static String fingerprint(final PlanHeader header, final List<PlanRow> rows) {
        final StringBuilder text = new StringBuilder();
        text.append(header.direction()).append('\u0001').append(header.qualification()).append('\u0001')
            .append(header.studyTerm()).append('\n');
        for (final PlanRow row : rows) {
            text.append(row.index()).append('\u0001').append(row.name()).append('\u0001')
                .append(String.join("\u0001", row.controls())).append('\u0001')
                .append(row.creditColumns()).append('\u0001').append(row.hours()).append('\n');
        }
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder();
            for (final byte octet : digest) {
                hex.append(String.format("%02x", octet));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", error);
        }
    }
}
