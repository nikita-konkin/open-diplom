package org.opendiplom.storage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.opendiplom.printing.BlankTemplate;
import org.opendiplom.printing.Calibration;

/**
 * What printing on blanks needs besides the registry (ADR-0011): the
 * FastReport templates the university uploads, one a document and level, and
 * the calibration of its printer.
 */
public final class Blanks {
    public static final String DIPLOMA = "diploma";
    public static final String SUPPLEMENT = "supplement";
    /** The documents, in the order the pages list them. */
    public static final Map<String, String> KINDS;
    /** Levels by the middle of the code of a direction: 09.03.02 is бакалавриат. */
    public static final Map<String, String> LEVELS;
    private static final String MAIN = "main";

    static {
        final Map<String, String> kinds = new LinkedHashMap<>();
        kinds.put(DIPLOMA, "Диплом");
        kinds.put(SUPPLEMENT, "Приложение к диплому");
        KINDS = Collections.unmodifiableMap(kinds);
        final Map<String, String> levels = new LinkedHashMap<>();
        levels.put("03", "бакалавриат");
        levels.put("04", "магистратура");
        levels.put("05", "специалитет");
        LEVELS = Collections.unmodifiableMap(levels);
    }

    private final Database database;

    public Blanks(final Database database) {
        this.database = database;
    }

    /** An uploaded template. */
    public static final class Template {
        public final String kind;
        public final String level;
        public final String fileName;
        public final byte[] content;
        public final String uploadedAt;

        Template(final String kind, final String level, final String fileName, final byte[] content,
            final String uploadedAt) {
            this.kind = kind;
            this.level = level;
            this.fileName = fileName;
            this.content = content;
            this.uploadedAt = uploadedAt;
        }
    }

    /** The level of a direction code, «03» for 09.03.02; empty when the code is not one. */
    public static String level(final String directionCode) {
        return directionCode != null && directionCode.matches("\\d{2}\\.\\d{2}\\.\\d{2}")
            ? directionCode.substring(3, 5) : "";
    }

    /** Every uploaded template, in the order of {@link #KINDS} and {@link #LEVELS}. */
    public List<Template> all() throws SQLException {
        final List<Template> all = new ArrayList<>();
        for (final String kind : KINDS.keySet()) {
            for (final String level : LEVELS.keySet()) {
                final Template template = this.find(kind, level);
                if (template != null) {
                    all.add(template);
                }
            }
        }
        return all;
    }

    /** The template of a document for a level, {@code null} when none is uploaded. */
    public Template find(final String kind, final String level) throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM blank_template WHERE kind = ? AND level = ?"
             )) {
            query.setString(1, kind);
            query.setString(2, level);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? new Template(
                    row.getString("kind"), row.getString("level"), row.getString("file_name"), row.getBytes("content"),
                    row.getString("uploaded_at")
                ) : null;
            }
        }
    }

    /**
     * Keeps a template in place of the one before.
     *
     * @throws IllegalArgumentException the document or the level is unknown, or the file is not a template
     */
    public void save(final String kind, final String level, final String fileName, final byte[] content)
        throws SQLException {
        if (!KINDS.containsKey(kind) || !LEVELS.containsKey(level)) {
            throw new IllegalArgumentException("Неизвестный документ или уровень образования: " + kind + ", " + level);
        }
        try {
            BlankTemplate.of(content);
        } catch (final IOException error) {
            throw new IllegalArgumentException("Файл «" + fileName + "» не прочитан как шаблон: " + error.getMessage(),
                error);
        }
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM blank_template WHERE kind = ? AND level = ?"
            )) {
                delete.setString(1, kind);
                delete.setString(2, level);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO blank_template (kind, level, file_name, content, uploaded_at) VALUES (?, ?, ?, ?, ?)"
            )) {
                insert.setString(1, kind);
                insert.setString(2, level);
                insert.setString(3, fileName);
                insert.setBytes(4, content);
                insert.setString(5, Instant.now().toString());
                insert.executeUpdate();
            }
            audit(connection, kind + "-" + level, "uploaded", fileName + ", " + content.length + " байт");
            connection.commit();
        }
    }

    /** Removes the template of a document for a level; nothing when there is none. */
    public void delete(final String kind, final String level) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM blank_template WHERE kind = ? AND level = ?"
            )) {
                delete.setString(1, kind);
                delete.setString(2, level);
                if (delete.executeUpdate() > 0) {
                    audit(connection, kind + "-" + level, "deleted", "");
                }
            }
            connection.commit();
        }
    }

    /** The calibration of the printer, none until it is saved. */
    public Calibration calibration() throws SQLException {
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement("SELECT dx, dy FROM printer WHERE id = ?")) {
            query.setString(1, MAIN);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? new Calibration(row.getFloat("dx"), row.getFloat("dy")) : Calibration.NONE;
            }
        }
    }

    public void calibration(final Calibration calibration) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM printer WHERE id = ?")) {
                delete.setString(1, MAIN);
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO printer (id, dx, dy, updated_at) VALUES (?, ?, ?, ?)"
            )) {
                insert.setString(1, MAIN);
                insert.setFloat(2, calibration.dx());
                insert.setFloat(3, calibration.dy());
                insert.setString(4, Instant.now().toString());
                insert.executeUpdate();
            }
            audit(connection, MAIN, "calibrated", String.format(
                Locale.ROOT, "вправо %.1f мм, вниз %.1f мм", calibration.dx(), calibration.dy()
            ));
            connection.commit();
        }
    }

    private static void audit(final Connection connection, final String subject, final String action,
        final String details) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO audit (id, at, subject, subject_id, action, details) VALUES (?, ?, 'blank', ?, ?, ?)"
        )) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, Instant.now().toString());
            insert.setString(3, subject);
            insert.setString(4, action);
            insert.setString(5, details);
            insert.executeUpdate();
        }
    }
}
