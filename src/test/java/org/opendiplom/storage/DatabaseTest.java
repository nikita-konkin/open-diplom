package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.opendiplom.Books.row;
import static org.opendiplom.Books.student;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Books;
import org.opendiplom.imports.CreditCheck;
import org.opendiplom.imports.StatementImport;

final class DatabaseTest {
    @TempDir
    Path folder;

    private String url() {
        return "jdbc:sqlite:" + this.folder.resolve("open-diplom.db");
    }

    @Test
    void cannotLoseSavedImportAfterRestart() throws Exception {
        final StatementImport statement = StatementImport.read(Books.statement(
            student("Тестов Т. Т.", row("Математика", 108, null, 5, null))
        ));
        final List<CreditCheck> checks = CreditCheck.settle(statement, null, "учебный план не загружен");
        final String batch = new ImportBatches(Database.open(this.url()))
            .save("Ведомость.xlsx", null, statement, checks);
        assertEquals(
            "5",
            new ImportBatches(Database.open(this.url())).grade(batch, "Тестов Т. Т.", "Математика_дисциплина_3"),
            "A grade saved in SQLite was not read back after reopening the database"
        );
    }

    @Test
    void cannotApplyMigrationTwice() throws Exception {
        Database.open(this.url());
        assertEquals(1, Database.open(this.url()).version(), "Reopening the database re-applied its migrations");
    }
}
