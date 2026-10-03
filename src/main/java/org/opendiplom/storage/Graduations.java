package org.opendiplom.storage;

import java.math.BigDecimal;
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
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.graduation.StudentMatch;
import org.opendiplom.graduation.SubjectMatch;

/**
 * Graduations (ADR-0009): a draft in the staging zone while the operator
 * matches its files, then graduates and results in the registry. Confirming
 * the same group of the same curriculum again replaces its graduates.
 *
 * <p>A statement often comes before every subject is graded, and the group is
 * loaded again with a fuller one. So the operator's choices about students
 * stay with the registered graduation and go to the next loading of the
 * group; the choices about subjects become links of the program.
 */
public final class Graduations {
    public static final String STAGING = "staging";
    public static final String REGISTERED = "registered";

    private static final Map<String, String> ACTIONS = Map.of(
        "staged", "файлы загружены", "registered", "записан в картотеку",
        "replaced", "выпускники заменены повторным импортом", "chairman", "председатель ГЭК"
    );

    private final Database database;

    public Graduations(final Database database) {
        this.database = database;
    }

    /** A graduation without its graduates. */
    public static final class Graduation {
        public final String id;
        public final String groupName;
        public final String status;
        public final String studyForm;
        public final Integer admissionYear;
        public final String curriculumId;
        public final String statementFile;
        public final String infoFile;
        public final String gekChairman;
        public final String createdAt;
        public final String updatedAt;

        Graduation(final ResultSet row) throws SQLException {
            this.id = row.getString("id");
            this.groupName = row.getString("group_name");
            this.status = row.getString("status");
            this.studyForm = row.getString("study_form");
            final int year = row.getInt("admission_year");
            this.admissionYear = row.wasNull() ? null : year;
            this.curriculumId = row.getString("curriculum_id");
            this.statementFile = row.getString("statement_file");
            this.infoFile = row.getString("info_file");
            this.gekChairman = row.getString("gek_chairman");
            this.createdAt = row.getString("created_at");
            this.updatedAt = row.getString("updated_at");
        }

        public boolean staging() {
            return STAGING.equals(this.status);
        }
    }

    /**
     * Starts a graduation in the staging zone and returns its identifier. The
     * choices about students of the registered graduation of the same group,
     * form and year come along.
     */
    public String stage(
        final String groupName, final String studyForm, final Integer admissionYear, final String statementFile,
        final String infoFile
    ) throws SQLException {
        final String id = UUID.randomUUID().toString();
        final String now = Instant.now().toString();
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO graduation (id, group_name, status, study_form, admission_year, curriculum_id, "
                    + "statement_file, info_file, gek_chairman, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, NULL, ?, ?, '', ?, ?)"
            )) {
                insert.setString(1, id);
                insert.setString(2, groupName);
                insert.setString(3, STAGING);
                insert.setString(4, studyForm);
                if (admissionYear == null) {
                    insert.setNull(5, Types.INTEGER);
                } else {
                    insert.setInt(5, admissionYear);
                }
                insert.setString(6, statementFile);
                insert.setString(7, infoFile);
                insert.setString(8, now);
                insert.setString(9, now);
                insert.executeUpdate();
            }
            audit(connection, id, "staged", groupName + ": " + statementFile + ", " + infoFile);
            if (admissionYear != null) {
                try (PreparedStatement copy = connection.prepareStatement(
                    "INSERT INTO staging_choice (graduation_id, item, choice) SELECT ?, item, choice FROM staging_choice "
                        + "WHERE (item LIKE ? OR item LIKE ?) AND graduation_id = (SELECT id FROM graduation "
                        + "WHERE status = ? AND group_name = ? AND study_form = ? AND admission_year = ? "
                        + "ORDER BY updated_at DESC LIMIT 1)"
                )) {
                    copy.setString(1, id);
                    copy.setString(2, StudentMatch.INFO + "%");
                    copy.setString(3, StudentMatch.SHEET + "%");
                    copy.setString(4, REGISTERED);
                    copy.setString(5, groupName);
                    copy.setString(6, studyForm);
                    copy.setInt(7, admissionYear);
                    copy.executeUpdate();
                }
            }
            connection.commit();
        }
        return id;
    }

    /** A graduation, or {@code null} when there is none with the identifier. */
    public Graduation find(final String id) throws SQLException {
        final List<Graduation> found = this.query("SELECT * FROM graduation WHERE id = ?", id);
        return found.isEmpty() ? null : found.get(0);
    }

    /** All graduations, the latest changed first. */
    public List<Graduation> all() throws SQLException {
        return this.query("SELECT * FROM graduation ORDER BY updated_at DESC");
    }

    /** What the operator decided while matching, by item; of a registered graduation, about its students. */
    public Map<String, String> choices(final String id) throws SQLException {
        final Map<String, String> choices = new LinkedHashMap<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT item, choice FROM staging_choice WHERE graduation_id = ?"
             )) {
            query.setString(1, id);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    choices.put(row.getString(1), row.getString(2));
                }
            }
        }
        return choices;
    }

    /**
     * Keeps the operator's decisions; an empty choice forgets the item.
     *
     * @param forget items starting with it are forgotten first, {@code null} for none
     */
    public void choose(final String id, final Map<String, String> choices, final String forget) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            if (forget != null) {
                try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM staging_choice WHERE graduation_id = ? AND item LIKE ?"
                )) {
                    delete.setString(1, id);
                    delete.setString(2, forget + "%");
                    delete.executeUpdate();
                }
            }
            for (final Map.Entry<String, String> choice : choices.entrySet()) {
                try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM staging_choice WHERE graduation_id = ? AND item = ?"
                )) {
                    delete.setString(1, id);
                    delete.setString(2, choice.getKey());
                    delete.executeUpdate();
                }
                if (choice.getValue() != null && !choice.getValue().isEmpty()) {
                    try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO staging_choice (graduation_id, item, choice) VALUES (?, ?, ?)"
                    )) {
                        insert.setString(1, id);
                        insert.setString(2, choice.getKey());
                        insert.setString(3, choice.getValue());
                        insert.executeUpdate();
                    }
                }
            }
            connection.commit();
        }
    }

    /** Sets the edition the graduation is checked against. */
    public void curriculum(final String id, final String curriculumId) throws SQLException {
        this.update("UPDATE graduation SET curriculum_id = ?, updated_at = ? WHERE id = ?", curriculumId, id);
    }

    /** Sets the chairman of the ГЭК, which no file has. */
    public void chairman(final String id, final String name) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE graduation SET gek_chairman = ?, updated_at = ? WHERE id = ?"
            )) {
                update.setString(1, name);
                update.setString(2, Instant.now().toString());
                update.setString(3, id);
                update.executeUpdate();
            }
            audit(connection, id, "chairman", name);
            connection.commit();
        }
    }

    /** Links confirmed for a program, by subject key. */
    public Map<String, SubjectMatch.Link> links(final String programId) throws SQLException {
        final Map<String, SubjectMatch.Link> links = new LinkedHashMap<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM subject_link WHERE program_id = ?"
             )) {
            query.setString(1, programId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    links.put(row.getString("subject_key"), new SubjectMatch.Link(
                        row.getString("subject_key"), row.getString("element_index"), row.getString("element_key"),
                        row.getString("alternative_key")
                    ));
                }
            }
        }
        return links;
    }

    /**
     * Writes a staged graduation to the registry; of its choices, those about
     * students stay with it. When the same group of the same curriculum is
     * there already, its graduates and choices are replaced and it keeps its
     * identifier and chairman.
     *
     * @return the identifier of the registered graduation
     */
    public String register(
        final String id, final List<GraduateRecord> graduates, final String programId,
        final List<SubjectMatch.Link> links
    ) throws SQLException {
        final Graduation staged = this.find(id);
        if (staged == null || !staged.staging() || staged.curriculumId == null) {
            throw new IllegalStateException("Выпуск не в промежуточной зоне или без учебного плана");
        }
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final String earlier = earlier(connection, staged);
            final String target = earlier == null ? id : earlier;
            final String now = Instant.now().toString();
            if (earlier != null) {
                delete(connection, "DELETE FROM result WHERE graduate_id IN "
                    + "(SELECT id FROM graduate WHERE graduation_id = ?)", earlier);
                delete(connection, "DELETE FROM graduate WHERE graduation_id = ?", earlier);
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE graduation SET curriculum_id = ?, study_form = ?, admission_year = ?, statement_file = ?, "
                        + "info_file = ?, updated_at = ? WHERE id = ?"
                )) {
                    update.setString(1, staged.curriculumId);
                    update.setString(2, staged.studyForm);
                    if (staged.admissionYear == null) {
                        update.setNull(3, Types.INTEGER);
                    } else {
                        update.setInt(3, staged.admissionYear);
                    }
                    update.setString(4, staged.statementFile);
                    update.setString(5, staged.infoFile);
                    update.setString(6, now);
                    update.setString(7, earlier);
                    update.executeUpdate();
                }
                delete(connection, "DELETE FROM staging_choice WHERE graduation_id = ?", earlier);
                try (PreparedStatement move = connection.prepareStatement(
                    "UPDATE staging_choice SET graduation_id = ? WHERE graduation_id = ? AND (item LIKE ? OR item LIKE ?)"
                )) {
                    move.setString(1, earlier);
                    move.setString(2, id);
                    move.setString(3, StudentMatch.INFO + "%");
                    move.setString(4, StudentMatch.SHEET + "%");
                    move.executeUpdate();
                }
                delete(connection, "DELETE FROM staging_choice WHERE graduation_id = ?", id);
                delete(connection, "DELETE FROM audit WHERE subject = 'graduation' AND subject_id = ?", id);
                delete(connection, "DELETE FROM graduation WHERE id = ?", id);
                audit(connection, earlier, "replaced", staged.statementFile + ", " + staged.infoFile + ", выпускников: "
                    + graduates.size());
            } else {
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE graduation SET status = ?, updated_at = ? WHERE id = ?"
                )) {
                    update.setString(1, REGISTERED);
                    update.setString(2, now);
                    update.setString(3, id);
                    update.executeUpdate();
                }
                try (PreparedStatement forget = connection.prepareStatement(
                    "DELETE FROM staging_choice WHERE graduation_id = ? AND NOT (item LIKE ? OR item LIKE ?)"
                )) {
                    forget.setString(1, id);
                    forget.setString(2, StudentMatch.INFO + "%");
                    forget.setString(3, StudentMatch.SHEET + "%");
                    forget.executeUpdate();
                }
                audit(connection, id, "registered", "выпускников: " + graduates.size());
            }
            for (final GraduateRecord graduate : graduates) {
                insert(connection, target, graduate);
            }
            for (final SubjectMatch.Link link : links) {
                try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM subject_link WHERE program_id = ? AND subject_key = ?"
                )) {
                    delete.setString(1, programId);
                    delete.setString(2, link.subjectKey);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO subject_link (program_id, subject_key, element_index, element_key, alternative_key, "
                        + "created_at) VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, programId);
                    insert.setString(2, link.subjectKey);
                    insert.setString(3, link.elementIndex);
                    insert.setString(4, link.elementKey);
                    insert.setString(5, link.alternativeKey);
                    insert.setString(6, now);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            return target;
        }
    }

    /** Deletes a graduation still in the staging zone, with its choices. */
    public void delete(final String id) throws SQLException {
        final Graduation found = this.find(id);
        if (found == null || !found.staging()) {
            throw new IllegalStateException("Удалить можно только выпуск в промежуточной зоне");
        }
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            delete(connection, "DELETE FROM staging_choice WHERE graduation_id = ?", id);
            delete(connection, "DELETE FROM audit WHERE subject = 'graduation' AND subject_id = ?", id);
            delete(connection, "DELETE FROM graduation WHERE id = ?", id);
            connection.commit();
        }
    }

    /** Graduates of a graduation in the order of the information file, with their results in the order of the plan. */
    public List<GraduateRecord> graduates(final String graduationId) throws SQLException {
        final List<GraduateRecord> graduates = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM graduate WHERE graduation_id = ? ORDER BY position"
             );
             PreparedStatement results = connection.prepareStatement(
                 // a course work after its discipline: «дисциплина» sorts before «курсовая»
                 "SELECT * FROM result WHERE graduate_id = ? ORDER BY element, kind"
             )) {
            query.setString(1, graduationId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    final List<ResultRecord> own = new ArrayList<>();
                    results.setString(1, row.getString("id"));
                    try (ResultSet result = results.executeQuery()) {
                        while (result.next()) {
                            final int grade = result.getInt("grade");
                            final Integer code = result.wasNull() ? null : grade;
                            final double value = result.getDouble("credits");
                            final Double credits = result.wasNull() ? null : value;
                            own.add(new ResultRecord(
                                result.getInt("element"), result.getString("kind"), result.getString("printed"), code,
                                result.getString("grade_text"), credits
                            ));
                        }
                    }
                    graduates.add(new GraduateRecord(
                        row.getString("id"), row.getInt("position"), row.getString("last_name"),
                        row.getString("first_name"), row.getString("middle_name"), row.getString("birth_date"),
                        row.getString("previous_document"), row.getString("previous_year"), row.getString("gek_date"),
                        row.getString("gek_protocol"), row.getString("thesis_topic"), integer(row, "thesis_grade"),
                        integer(row, "state_exam_grade"), row.getString("statement_name"),
                        row.getString("student_number"), row.getString("notes"), own
                    ));
                }
            }
        }
        return graduates;
    }

    /** What was done to a graduation, oldest first: «время — действие — подробности». */
    public List<String> history(final String id) throws SQLException {
        final List<String> history = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT at, action, details FROM audit WHERE subject = 'graduation' AND subject_id = ? ORDER BY at"
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

    /** The registered graduation of the same group, program, form and year, or {@code null}. */
    private static String earlier(final Connection connection, final Graduation staged) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT g.id FROM graduation g JOIN curriculum c ON c.id = g.curriculum_id "
                + "JOIN curriculum s ON s.id = ? "
                + "WHERE g.status = ? AND g.group_name = ? AND g.id <> ? AND c.program_id = s.program_id "
                + "AND c.study_form = s.study_form AND c.admission_year = s.admission_year "
                + "ORDER BY g.created_at LIMIT 1"
        )) {
            query.setString(1, staged.curriculumId);
            query.setString(2, REGISTERED);
            query.setString(3, staged.groupName);
            query.setString(4, staged.id);
            try (ResultSet found = query.executeQuery()) {
                return found.next() ? found.getString(1) : null;
            }
        }
    }

    private static void insert(final Connection connection, final String graduation, final GraduateRecord graduate)
        throws SQLException {
        final String id = UUID.randomUUID().toString();
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO graduate (id, graduation_id, position, last_name, first_name, middle_name, birth_date, "
                + "previous_document, previous_year, gek_date, gek_protocol, thesis_topic, thesis_grade, "
                + "state_exam_grade, statement_name, student_number, notes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            int column = 1;
            insert.setString(column++, id);
            insert.setString(column++, graduation);
            insert.setInt(column++, graduate.position);
            insert.setString(column++, graduate.lastName);
            insert.setString(column++, graduate.firstName);
            insert.setString(column++, graduate.middleName);
            insert.setString(column++, graduate.birthDate);
            insert.setString(column++, graduate.previousDocument);
            insert.setString(column++, graduate.previousYear);
            insert.setString(column++, graduate.gekDate);
            insert.setString(column++, graduate.gekProtocol);
            insert.setString(column++, graduate.thesisTopic);
            integer(insert, column++, graduate.thesisGrade);
            integer(insert, column++, graduate.stateExamGrade);
            insert.setString(column++, graduate.statementName);
            insert.setString(column++, graduate.studentNumber);
            insert.setString(column, graduate.notes.length() > 4000 ? graduate.notes.substring(0, 4000) : graduate.notes);
            insert.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO result (graduate_id, element, kind, printed, grade, grade_text, credits) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)"
        )) {
            for (final ResultRecord result : graduate.results) {
                insert.setString(1, id);
                insert.setInt(2, result.element);
                insert.setString(3, result.kind);
                insert.setString(4, result.printed);
                integer(insert, 5, result.grade);
                insert.setString(6, result.gradeText);
                if (result.credits == null) {
                    insert.setNull(7, Types.NUMERIC);
                } else {
                    insert.setBigDecimal(7, BigDecimal.valueOf(result.credits));
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<Graduation> query(final String sql, final Object... parameters) throws SQLException {
        final List<Graduation> found = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(sql)) {
            for (int position = 0; position < parameters.length; ++position) {
                query.setObject(position + 1, parameters[position]);
            }
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    found.add(new Graduation(row));
                }
            }
        }
        return found;
    }

    private void update(final String sql, final String value, final String id) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, value);
            update.setString(2, Instant.now().toString());
            update.setString(3, id);
            update.executeUpdate();
        }
    }

    private static void delete(final Connection connection, final String sql, final String id) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(sql)) {
            delete.setString(1, id);
            delete.executeUpdate();
        }
    }

    private static void audit(
        final Connection connection, final String id, final String action, final String details
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO audit (id, at, subject, subject_id, action, details) VALUES (?, ?, 'graduation', ?, ?, ?)"
        )) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, Instant.now().toString());
            insert.setString(3, id);
            insert.setString(4, action);
            insert.setString(5, details.length() > 4000 ? details.substring(0, 4000) : details);
            insert.executeUpdate();
        }
    }

    private static Integer integer(final ResultSet row, final String column) throws SQLException {
        final int value = row.getInt(column);
        return row.wasNull() ? null : value;
    }

    private static void integer(final PreparedStatement statement, final int column, final Integer value)
        throws SQLException {
        if (value == null) {
            statement.setNull(column, Types.INTEGER);
        } else {
            statement.setInt(column, value);
        }
    }
}
