package org.opendiplom.imports;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.WorkbookException;

/**
 * A curriculum saved to PDF from «Планы»: the table «План учебного процесса»
 * read by its grid.
 *
 * <p>A long name wraps to two or three lines around the line of its index, as
 * far from it as the next row is, so rows are taken between the horizontal
 * rules of the table and cells between its vertical rules. Under the titles
 * the columns are numbered (1 — index, 2 — name, ...), and where they stand
 * differs from plan to plan: the credits are the first column under «в
 * зачетных единицах», the element is under «Структура ОП».
 */
final class PlanPdf {
    private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final int SIGNATURE_WINDOW = 1024;
    /** Points: rules closer than this are one rule, glyphs closer in height are one line. */
    private static final float SAME = 1.5f;
    /** Points: a filled rectangle this thin is drawn as a rule. */
    private static final float THIN = 2f;
    /** Points: fields of the title on one line stand further apart than words. */
    private static final float FIELD_GAP = 15f;
    /** Points: numbers in the row of column numbers stand further apart than digits. */
    private static final float NUMBER_GAP = 3f;
    /** Share of the font size: a gap wider than this between glyphs is a space. */
    private static final float SPACE = 0.2f;
    /** Share of the font size from the baseline to the middle of a glyph. */
    private static final float MIDDLE = 0.3f;
    private static final int NUMBERED = 8;
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final List<PlanRow> elements;
    private final List<String> title;
    private final int page;
    private final boolean indexed;

    private PlanPdf(final List<PlanRow> elements, final List<String> title, final int page, final boolean indexed) {
        this.elements = elements;
        this.title = title;
        this.page = page;
        this.indexed = indexed;
    }

    /** Whether the content is a PDF: its header may follow some bytes of junk. */
    static boolean pdf(final byte[] content) {
        final int end = Math.min(content.length, SIGNATURE_WINDOW) - SIGNATURE.length;
        for (int start = 0; start <= end; ++start) {
            int same = 0;
            while (same < SIGNATURE.length && content[start + same] == SIGNATURE[same]) {
                ++same;
            }
            if (same == SIGNATURE.length) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rows of the tables «План учебного процесса» of all pages.
     *
     * @throws WorkbookException the PDF cannot be opened, is a scan or has no such table
     */
    static PlanPdf read(final byte[] content) throws WorkbookException {
        try (PDDocument document = Loader.loadPDF(content)) {
            final List<PlanRow> elements = new ArrayList<>();
            List<String> title = null;
            int first = 0;
            boolean text = false;
            for (int number = 0; number < document.getNumberOfPages(); ++number) {
                final PDPage page = document.getPage(number);
                final List<Glyph> glyphs = glyphs(document, number);
                text |= !glyphs.isEmpty();
                if (glyphs.isEmpty() || page.getRotation() % 360 != 0) {
                    continue;
                }
                final Grid grid = Grid.of(page);
                final List<String> pieces = new ArrayList<>();
                if (table(glyphs, grid, elements, pieces) && title == null) {
                    title = pieces;
                    first = number + 1;
                }
            }
            if (!text) {
                throw new WorkbookException(
                    "Учебный план: в PDF нет текста, похоже, это скан. Загрузите PDF, "
                        + "сохранённый из «Планов», или план в Excel"
                );
            }
            if (title == null) {
                throw new WorkbookException(
                    "Учебный план: в PDF не найдена таблица «План учебного процесса» с колонкой "
                        + "«Объем частей ОП в зачетных единицах» и строкой номеров колонок. Загрузите "
                        + "PDF, сохранённый из «Планов», или план в Excel"
                );
            }
            boolean indexed = false;
            for (final PlanRow element : elements) {
                indexed |= !element.index.isEmpty();
            }
            return new PlanPdf(elements, title, first, indexed);
        } catch (final InvalidPasswordException error) {
            throw new WorkbookException("Учебный план: PDF защищён паролем. Сохраните его без пароля", error);
        } catch (final IOException | RuntimeException error) {
            // a damaged file is the operator's to replace, not a failure of the program
            throw new WorkbookException(
                "Учебный план: PDF не открывается (" + error.getMessage() + "). Сохраните план заново",
                error
            );
        }
    }

    /** Rows of the plan in the order printed, blocks, parts and the total included. */
    List<PlanRow> elements() {
        return this.elements;
    }

    /** Texts above the table, a piece per field. */
    List<String> title() {
        return this.title;
    }

    /** First page with the table, from 1. */
    int page() {
        return this.page;
    }

    /** Whether the table has an index column. */
    boolean indexed() {
        return this.indexed;
    }

    /**
     * Reads the table of a page into elements and the title above it into pieces.
     *
     * @return whether the page has the table
     */
    private static boolean table(
        final List<Glyph> glyphs, final Grid grid, final List<PlanRow> elements, final List<String> pieces
    ) {
        final List<List<Glyph>> lines = lines(glyphs);
        for (int head = 0; head < lines.size(); ++head) {
            final Glyph credits = find(lines.get(head), "зачетных единиц");
            if (credits == null) {
                continue;
            }
            for (int below = head + 1; below < lines.size(); ++below) {
                final List<float[]> numbered = numbered(lines.get(below), grid);
                if (numbered == null) {
                    continue;
                }
                final float[] column = under(numbered, grid, credits, false);
                if (column == null) {
                    break;
                }
                // the name is the last column under «Структура ОП», the index is left of it
                float[] name = numbered.get(1);
                for (int line = head; line < below; ++line) {
                    final Glyph structure = find(lines.get(line), "структура оп");
                    if (structure != null) {
                        name = under(numbered, grid, structure, true);
                    }
                }
                if (name == null) {
                    break;
                }
                final int position = numbered.indexOf(name);
                final float[] index = position > 0 ? numbered.get(position - 1) : name;
                // «Объем частей ОП в часах» stands next to the credits title
                List<float[]> hours = Collections.emptyList();
                for (int line = Math.max(0, head - 2); line < below; ++line) {
                    final Glyph title = find(lines.get(line), "в часах");
                    if (title != null) {
                        hours = within(numbered, grid, title);
                        break;
                    }
                }
                rows(glyphs, grid, middle(lines.get(below).get(0)), index, name, column, hours, elements);
                for (int line = 0; line < head; ++line) {
                    pieces.addAll(runs(lines.get(line), FIELD_GAP));
                }
                return true;
            }
        }
        return false;
    }

    /** Rows of the table below the column numbers. */
    private static void rows(
        final List<Glyph> glyphs, final Grid grid, final float numbers, final float[] index,
        final float[] name, final float[] credits, final List<float[]> hours, final List<PlanRow> elements
    ) {
        final List<Float> rules = new ArrayList<>();
        for (final float rule : Grid.crossing(grid.horizontal, (name[0] + name[1]) / 2)) {
            if (rule > numbers) {
                rules.add(rule);
            }
        }
        for (int row = 1; row < rules.size(); ++row) {
            final float top = rules.get(row - 1);
            final float bottom = rules.get(row);
            final List<Float> cells = Grid.crossing(grid.vertical, (top + bottom) / 2);
            final boolean apart = has(cells, index[1]) && index[1] < name[1];
            final String element = text(glyphs, apart ? name[0] : index[0], name[1], top, bottom);
            if (element.isEmpty()) {
                continue;
            }
            final List<Double> values = new ArrayList<>();
            for (final float[] column : hours) {
                values.add(value(glyphs, grid, column, top, bottom));
            }
            elements.add(new PlanRow(
                apart ? text(glyphs, index[0], index[1], top, bottom) : "",
                element,
                value(glyphs, grid, credits, top, bottom),
                values
            ));
        }
    }

    /**
     * The number in a column of a row. The row of the program total has two
     * lines: titles over merged cells and the numbers under them, so a line
     * counts only where the rules bound the column.
     */
    private static Double value(
        final List<Glyph> glyphs, final Grid grid, final float[] column, final float top, final float bottom
    ) {
        final List<Glyph> inside = new ArrayList<>();
        for (final Glyph glyph : glyphs) {
            final float x = glyph.x + glyph.width / 2;
            final float y = middle(glyph);
            if (column[0] <= x && x < column[1] && top < y && y < bottom) {
                inside.add(glyph);
            }
        }
        for (final List<Glyph> line : lines(inside)) {
            final List<Float> cells = Grid.crossing(grid.vertical, middle(line.get(0)));
            final String number = String.join(" ", runs(line, Float.MAX_VALUE)).replace(',', '.');
            if (has(cells, column[0]) && has(cells, column[1]) && NUMBER.matcher(number).matches()) {
                return Double.valueOf(number);
            }
        }
        return null;
    }

    /**
     * The first, or the last, numbered column within the cell holding a title.
     *
     * @return the column, or {@code null} when the cell has no numbered column
     */
    private static float[] under(
        final List<float[]> numbered, final Grid grid, final Glyph title, final boolean last
    ) {
        final List<float[]> columns = within(numbered, grid, title);
        if (columns.isEmpty()) {
            return null;
        }
        return columns.get(last ? columns.size() - 1 : 0);
    }

    /** The numbered columns within the cell holding a title, left to right. */
    private static List<float[]> within(final List<float[]> numbered, final Grid grid, final Glyph title) {
        final float[] cell = cell(Grid.crossing(grid.vertical, middle(title)), title.x + title.width / 2);
        final List<float[]> columns = new ArrayList<>();
        for (final float[] column : numbered) {
            if (cell != null && column[0] >= cell[0] - SAME && column[1] <= cell[1] + SAME) {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * Cells of a row of column numbers 1, 2, 3...; {@code null} when the line is
     * something else.
     */
    private static List<float[]> numbered(final List<Glyph> line, final Grid grid) {
        final List<Glyph> starts = new ArrayList<>();
        final List<String> numbers = runs(line, NUMBER_GAP, starts);
        if (numbers.size() < NUMBERED) {
            return null;
        }
        final List<Float> rules = Grid.crossing(grid.vertical, middle(line.get(0)));
        final List<float[]> cells = new ArrayList<>();
        for (int column = 0; column < numbers.size(); ++column) {
            if (!String.valueOf(column + 1).equals(numbers.get(column))) {
                return column < NUMBERED ? null : cells;
            }
            final float[] cell = cell(rules, starts.get(column).x);
            if (cell == null) {
                return null;
            }
            cells.add(cell);
        }
        return cells;
    }

    private static float[] cell(final List<Float> rules, final float x) {
        for (int edge = 1; edge < rules.size(); ++edge) {
            if (rules.get(edge - 1) <= x && x < rules.get(edge)) {
                return new float[] {rules.get(edge - 1), rules.get(edge)};
            }
        }
        return null;
    }

    private static boolean has(final List<Float> rules, final float at) {
        for (final float rule : rules) {
            if (Math.abs(rule - at) <= SAME) {
                return true;
            }
        }
        return false;
    }

    /** The first glyph of a run of the line that contains the text, case and ё aside. */
    private static Glyph find(final List<Glyph> line, final String text) {
        final List<Glyph> starts = new ArrayList<>();
        final List<String> runs = runs(line, FIELD_GAP, starts);
        for (int run = 0; run < runs.size(); ++run) {
            if (runs.get(run).toLowerCase(Locale.ROOT).replace('ё', 'е').contains(text)) {
                return starts.get(run);
            }
        }
        return null;
    }

    /** Text of the glyphs whose middles are inside a box, line by line. */
    private static String text(
        final List<Glyph> glyphs, final float left, final float right, final float top, final float bottom
    ) {
        final List<Glyph> inside = new ArrayList<>();
        for (final Glyph glyph : glyphs) {
            final float x = glyph.x + glyph.width / 2;
            final float y = middle(glyph);
            if (left <= x && x < right && top < y && y < bottom) {
                inside.add(glyph);
            }
        }
        final List<String> texts = new ArrayList<>();
        for (final List<Glyph> line : lines(inside)) {
            texts.addAll(runs(line, Float.MAX_VALUE));
        }
        return Cells.collapse(String.join(" ", texts));
    }

    /** Glyphs grouped by baseline, lines top to bottom, glyphs left to right. */
    private static List<List<Glyph>> lines(final List<Glyph> glyphs) {
        final List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort(Comparator.comparingDouble(glyph -> glyph.y));
        final List<List<Glyph>> lines = new ArrayList<>();
        List<Glyph> line = null;
        for (final Glyph glyph : sorted) {
            if (line == null || glyph.y - line.get(0).y > SAME) {
                line = new ArrayList<>();
                lines.add(line);
            }
            line.add(glyph);
        }
        for (final List<Glyph> each : lines) {
            each.sort(Comparator.comparingDouble(glyph -> glyph.x));
        }
        return lines;
    }

    private static List<String> runs(final List<Glyph> line, final float gap) {
        return runs(line, gap, new ArrayList<>());
    }

    /** Texts of a line split where glyphs stand further apart than the gap. */
    private static List<String> runs(final List<Glyph> line, final float gap, final List<Glyph> starts) {
        final List<String> runs = new ArrayList<>();
        final StringBuilder run = new StringBuilder();
        float end = Float.NaN;
        for (final Glyph glyph : line) {
            final float space = glyph.x - end;
            if (!Float.isNaN(end) && space > gap) {
                runs.add(Cells.collapse(run.toString()));
                run.setLength(0);
            } else if (!Float.isNaN(end) && space > glyph.size * SPACE) {
                run.append(' ');
            }
            if (run.length() == 0) {
                starts.add(glyph);
            }
            run.append(glyph.text);
            end = glyph.x + glyph.width;
        }
        if (run.length() > 0) {
            runs.add(Cells.collapse(run.toString()));
        }
        return runs;
    }

    private static float middle(final Glyph glyph) {
        return glyph.y - glyph.size * MIDDLE;
    }

    /** Upright glyphs of a page; the turned titles of the semester columns are left out. */
    private static List<Glyph> glyphs(final PDDocument document, final int page) throws IOException {
        final List<Glyph> glyphs = new ArrayList<>();
        final PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(final String text, final List<TextPosition> positions) {
                for (final TextPosition position : positions) {
                    if (position.getDir() == 0 && !position.getUnicode().isBlank()) {
                        glyphs.add(new Glyph(position));
                    }
                }
            }
        };
        stripper.setStartPage(page + 1);
        stripper.setEndPage(page + 1);
        stripper.getText(document);
        return glyphs;
    }

    /** A glyph in points from the top-left corner of the page, at its baseline. */
    private static final class Glyph {
        final float x;
        final float y;
        final float width;
        final float size;
        final String text;

        Glyph(final TextPosition position) {
            this.x = position.getXDirAdj();
            this.y = position.getYDirAdj();
            this.width = position.getWidthDirAdj();
            this.size = position.getFontSizeInPt() > 0 ? position.getFontSizeInPt() : position.getHeightDir();
            this.text = position.getUnicode();
        }
    }

    /**
     * Straight rules of a page in points from its top-left corner: stroked
     * lines and edges, and filled rectangles thin enough to be lines.
     */
    private static final class Grid extends PDFGraphicsStreamEngine {
        /** {y, left, right} */
        final List<float[]> horizontal = new ArrayList<>();
        /** {x, top, bottom} */
        final List<float[]> vertical = new ArrayList<>();
        private final List<float[]> path = new ArrayList<>();
        private final List<float[]> boxes = new ArrayList<>();
        private final float left;
        private final float top;
        private Point2D.Float current;
        private Point2D.Float start;

        private Grid(final PDPage page) {
            super(page);
            final PDRectangle crop = page.getCropBox();
            this.left = crop.getLowerLeftX();
            this.top = crop.getUpperRightY();
        }

        static Grid of(final PDPage page) throws IOException {
            final Grid grid = new Grid(page);
            grid.processPage(page);
            return grid;
        }

        /** Positions of the rules crossing a line, sorted, close ones as one. */
        static List<Float> crossing(final List<float[]> rules, final float at) {
            final List<Float> found = new ArrayList<>();
            for (final float[] rule : rules) {
                if (rule[1] - SAME <= at && at <= rule[2] + SAME) {
                    found.add(rule[0]);
                }
            }
            Collections.sort(found);
            final List<Float> merged = new ArrayList<>();
            float sum = 0;
            int count = 0;
            for (final float position : found) {
                if (count > 0 && position - sum / count > SAME) {
                    merged.add(sum / count);
                    sum = 0;
                    count = 0;
                }
                sum += position;
                ++count;
            }
            if (count > 0) {
                merged.add(sum / count);
            }
            return merged;
        }

        @Override
        public void appendRectangle(final Point2D p0, final Point2D p1, final Point2D p2, final Point2D p3) {
            final Point2D[] corners = {p0, p1, p2, p3};
            for (int corner = 0; corner < corners.length; ++corner) {
                this.segment(corners[corner], corners[(corner + 1) % corners.length]);
            }
            this.boxes.add(new float[] {
                (float) Math.min(p0.getX(), p2.getX()), (float) Math.min(p0.getY(), p2.getY()),
                (float) Math.max(p0.getX(), p2.getX()), (float) Math.max(p0.getY(), p2.getY()),
            });
        }

        @Override
        public void moveTo(final float x, final float y) {
            this.current = new Point2D.Float(x, y);
            this.start = this.current;
        }

        @Override
        public void lineTo(final float x, final float y) {
            final Point2D.Float next = new Point2D.Float(x, y);
            if (this.current != null) {
                this.segment(this.current, next);
            }
            this.current = next;
        }

        @Override
        public void curveTo(final float x1, final float y1, final float x2, final float y2, final float x3, final float y3) {
            this.current = new Point2D.Float(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return this.current;
        }

        @Override
        public void closePath() {
            if (this.current != null && this.start != null) {
                this.segment(this.current, this.start);
                this.current = this.start;
            }
        }

        @Override
        public void endPath() {
            this.path.clear();
            this.boxes.clear();
        }

        @Override
        public void strokePath() {
            for (final float[] segment : this.path) {
                this.rule(segment[0], segment[1], segment[2], segment[3]);
            }
            this.endPath();
        }

        @Override
        public void fillPath(final int windingRule) {
            for (final float[] box : this.boxes) {
                if (box[3] - box[1] <= THIN) {
                    final float y = (box[1] + box[3]) / 2;
                    this.rule(box[0], y, box[2], y);
                } else if (box[2] - box[0] <= THIN) {
                    final float x = (box[0] + box[2]) / 2;
                    this.rule(x, box[1], x, box[3]);
                }
            }
            this.endPath();
        }

        @Override
        public void fillAndStrokePath(final int windingRule) {
            this.strokePath();
        }

        @Override
        public void clip(final int windingRule) {
            // the clipping path is ended by the next painting operator or «n»
        }

        @Override
        public void shadingFill(final COSName shadingName) {
            // shading draws no rules
        }

        @Override
        public void drawImage(final PDImage pdImage) {
            // a scanned grid is not read
        }

        private void segment(final Point2D from, final Point2D to) {
            this.path.add(new float[] {(float) from.getX(), (float) from.getY(), (float) to.getX(), (float) to.getY()});
        }

        private void rule(final float x1, final float y1, final float x2, final float y2) {
            if (Math.abs(y1 - y2) < SAME / 3 && Math.abs(x1 - x2) >= 1) {
                this.horizontal.add(new float[] {
                    this.top - (y1 + y2) / 2, Math.min(x1, x2) - this.left, Math.max(x1, x2) - this.left,
                });
            } else if (Math.abs(x1 - x2) < SAME / 3 && Math.abs(y1 - y2) >= 1) {
                this.vertical.add(new float[] {
                    (x1 + x2) / 2 - this.left, this.top - Math.max(y1, y2), this.top - Math.min(y1, y2),
                });
            }
        }
    }
}
