package org.opendiplom.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * The database: a SQLite file on a single PC, PostgreSQL on a university
 * server (ADR-0002). Migrations are plain SQL that both understand.
 */
public final class Database {
    /** Migrations in the order they are applied; never edit an applied one. */
    private static final List<String> MIGRATIONS = Arrays.asList(
        "V1__import.sql", "V2__curricula.sql", "V3__graduations.sql", "V4__documents.sql"
    );

    private final String url;

    private Database(final String url) {
        this.url = url;
    }

    /**
     * Opens the database and brings its schema up to date.
     *
     * @param url JDBC URL, {@code jdbc:sqlite:data/open-diplom.db} on a PC
     */
    public static Database open(final String url) throws SQLException {
        final Database database = new Database(url);
        database.migrate();
        return database;
    }

    public Connection connection() throws SQLException {
        final Connection connection = DriverManager.getConnection(this.url);
        if (this.url.startsWith("jdbc:sqlite:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
        }
        return connection;
    }

    /** Number of the last applied migration. */
    public int version() throws SQLException {
        try (Connection connection = this.connection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private void migrate() throws SQLException {
        try (Connection connection = this.connection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS schema_version ("
                        + "version INTEGER PRIMARY KEY, name VARCHAR(200) NOT NULL, "
                        + "applied_at VARCHAR(40) NOT NULL)"
                );
            }
            connection.setAutoCommit(false);
            final int applied = this.applied(connection);
            for (int number = applied; number < MIGRATIONS.size(); ++number) {
                final String name = MIGRATIONS.get(number);
                try (Statement statement = connection.createStatement()) {
                    // a «;» in a comment must not end a statement
                    for (final String sql : script(name).replaceAll("(?m)^\\s*--.*$", "").split(";")) {
                        if (!sql.isBlank()) {
                            statement.execute(sql);
                        }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_version (version, name, applied_at) VALUES (?, ?, ?)"
                )) {
                    insert.setInt(1, number + 1);
                    insert.setString(2, name);
                    insert.setString(3, Instant.now().toString());
                    insert.executeUpdate();
                }
                connection.commit();
            }
        }
    }

    private int applied(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static String script(final String name) throws SQLException {
        try (InputStream in = Database.class.getResourceAsStream("/db/migration/" + name)) {
            if (in == null) {
                throw new SQLException("Нет файла миграции " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException error) {
            throw new SQLException("Не удалось прочитать миграцию " + name, error);
        }
    }
}
