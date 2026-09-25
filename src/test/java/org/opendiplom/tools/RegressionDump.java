package org.opendiplom.tools;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opendiplom.export.CyberDiplomaXml;
import org.opendiplom.export.Graduate;
import org.opendiplom.export.PivotSource;
import org.opendiplom.export.Program;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.imports.CreditCheck;
import org.opendiplom.imports.Curriculum;
import org.opendiplom.imports.StatementImport;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;
import org.opendiplom.sheets.WorkbookException;
import org.opendiplom.sheets.Workbooks;

/**
 * Dumps what the Java code reads from real files, as JSON lines, for comparison
 * with the Python service (tools/regression/compare.py). Not part of the program.
 *
 * <p>Usage: {@code RegressionDump statement|plan <file>...} or
 * {@code RegressionDump xml <pivot> <info>}. The output holds personal data:
 * keep it on the machine that has the files.
 */
public final class RegressionDump {
    private RegressionDump() {
    }

    public static void main(final String... args) throws Exception {
        System.setProperty(
            "log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory"
        );
        final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        final List<String> lines = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
        for (final String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            final String[] files = line.split("\t");
            final Json json = new Json().key("files").strings(List.of(files));
            try {
                if ("statement".equals(args[0])) {
                    statement(json, files);
                } else if ("plan".equals(args[0])) {
                    plan(json, files[0]);
                } else {
                    xml(json, files[0], files[1]);
                }
            } catch (final WorkbookException | ValidationProblems error) {
                json.key("error").string(error.getMessage());
            } catch (final Exception error) {
                json.key("crash").string(error.getClass().getSimpleName());
            }
            out.println(json.end());
        }
    }

    private static void statement(final Json json, final String... files) throws Exception {
        final StatementImport statement = StatementImport.read(Files.readAllBytes(Paths.get(files[0])));
        Curriculum curriculum = null;
        String problem = "учебный план не загружен";
        if (files.length > 1) {
            try {
                curriculum = Curriculum.read(Files.readAllBytes(Paths.get(files[1])));
            } catch (final WorkbookException error) {
                problem = "учебный план не прочитан (" + error.getMessage() + ")";
            }
        }
        json.key("students").strings(statement.students());
        json.key("labels").strings(statement.labels());
        json.key("grades").object();
        for (final String label : statement.labels()) {
            final List<String> grades = new ArrayList<>();
            for (final String student : statement.students()) {
                grades.add(Cells.text(statement.grade(student, label)));
            }
            json.key(label).strings(grades);
        }
        json.close('}');
        json.key("checks").array();
        for (final CreditCheck check : CreditCheck.settle(statement, curriculum, problem)) {
            json.array().string(check.settled()).number(check.counted())
                .number(check.planned()).string(check.source()).string(check.notes()).close(']');
        }
        json.close(']');
    }

    private static void plan(final Json json, final String file) throws Exception {
        final Curriculum curriculum = Curriculum.read(Files.readAllBytes(Paths.get(file)));
        json.key("sheet").string(curriculum.sheet());
        json.key("credits").object();
        for (final Map.Entry<String, Double> entry : curriculum.credits().entrySet()) {
            json.key(entry.getKey()).number(entry.getValue());
        }
        json.close('}');
    }

    private static void xml(final Json json, final String pivot, final String info) throws Exception {
        final Program program = new Program.Builder()
            .studyTerm("4 года").qualification("бакалавр").studyForm("очная")
            .direction("11.03.02 ИНФОКОММУНИКАЦИОННЫЕ ТЕХНОЛОГИИ И СИСТЕМЫ СВЯЗИ")
            .profile("Интеллектуальные телекоммуникационные системы и сети")
            .programCredits(240).contactHours("3180 ак.час").practiceCredits(12)
            .finalCredits(9).gekChairman("Председатель ГЭК")
            .build();
        final List<Graduate> graduates = PivotSource.read(
            first(pivot, "Сводная таблица"), first(info, "Сведения о студентах"), LocalDate.now()
        );
        json.key("xml").string(CyberDiplomaXml.write(program, graduates));
    }

    private static Sheet first(final String file, final String title) throws Exception {
        return Workbooks.read(Files.readAllBytes(Paths.get(file)), title).get(0);
    }

    /** Just enough JSON for this dump. */
    private static final class Json {
        private final StringBuilder text = new StringBuilder("{");
        private boolean comma;

        Json key(final String key) {
            this.string(key);
            this.text.append(':');
            this.comma = false;
            return this;
        }

        Json string(final String value) {
            this.separate();
            this.text.append('"');
            for (final char letter : value.toCharArray()) {
                if (letter == '"' || letter == '\\') {
                    this.text.append('\\').append(letter);
                } else if (letter < 0x20) {
                    this.text.append(String.format("\\u%04x", (int) letter));
                } else {
                    this.text.append(letter);
                }
            }
            this.text.append('"');
            return this;
        }

        Json strings(final List<String> values) {
            this.array();
            values.forEach(this::string);
            return this.close(']');
        }

        Json number(final Number value) {
            this.separate();
            this.text.append(value == null ? "null" : value.toString());
            return this;
        }

        Json array() {
            this.separate();
            this.text.append('[');
            this.comma = false;
            return this;
        }

        Json object() {
            this.separate();
            this.text.append('{');
            this.comma = false;
            return this;
        }

        Json close(final char bracket) {
            this.text.append(bracket);
            this.comma = true;
            return this;
        }

        String end() {
            return this.text.append('}').toString();
        }

        private void separate() {
            if (this.comma) {
                this.text.append(',');
            }
            this.comma = true;
        }
    }
}
