package org.opendiplom.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendiplom.Templates;
import org.opendiplom.printing.Calibration;

/** The templates of the blanks and the calibration of the printer in the database. */
final class BlanksTest {
    @TempDir
    Path folder;

    private Blanks blanks;

    @BeforeEach
    void open() throws Exception {
        this.blanks = new Blanks(Database.open("jdbc:sqlite:" + this.folder.resolve("test.db")));
    }

    @Test
    void cannotKeepTwoTemplatesOfOneDocumentAndLevel() throws Exception {
        this.blanks.save(Blanks.SUPPLEMENT, "03", "старый.fr3", Templates.supplement());
        final byte[] newer = Templates.gzip(Templates.supplement());
        this.blanks.save(Blanks.SUPPLEMENT, "03", "новый.fr3", newer);
        final Blanks.Template found = this.blanks.find(Blanks.SUPPLEMENT, "03");
        assertEquals("новый.fr3", found.fileName, "The template uploaded later did not replace the earlier");
        assertArrayEquals(newer, found.content, "The template came back changed");
        assertEquals(1, this.blanks.all().size(), "Two templates are kept for one document and level");
        assertNull(this.blanks.find(Blanks.SUPPLEMENT, "04"), "The template of one level went to another");
        this.blanks.delete(Blanks.SUPPLEMENT, "03");
        assertNull(this.blanks.find(Blanks.SUPPLEMENT, "03"), "The template was not deleted");
    }

    @Test
    void cannotKeepFileThatIsNotTemplate() {
        assertThrows(IllegalArgumentException.class, () -> this.blanks.save(
            Blanks.DIPLOMA, "03", "диплом.docx", "PK не шаблон".getBytes(StandardCharsets.UTF_8)
        ), "A file that is not a FastReport template was kept as one");
        assertThrows(IllegalArgumentException.class, () -> this.blanks.save(
            "certificate", "03", "справка.fr3", Templates.diploma()
        ), "A template of an unknown document was kept");
    }

    @Test
    void cannotForgetCalibration() throws Exception {
        assertEquals(0, this.blanks.calibration().dx(), "A printer never calibrated has a shift");
        this.blanks.calibration(new Calibration(1.5f, -0.5f));
        this.blanks.calibration(new Calibration(-2, 3));
        assertEquals(-2, this.blanks.calibration().dx(), 1e-6, "The shift to the right was not kept");
        assertEquals(3, this.blanks.calibration().dy(), 1e-6, "The shift down was not kept");
    }

    @Test
    void cannotTakeLevelFromCodeWrong() {
        assertEquals("03", Blanks.level("09.03.02"), "Бакалавриат was not found in the code of the direction");
        assertEquals("05", Blanks.level("10.05.03"), "Специалитет was not found in the code of the direction");
        assertEquals("", Blanks.level("Информационные системы"), "A level was found where there is no code");
    }
}
