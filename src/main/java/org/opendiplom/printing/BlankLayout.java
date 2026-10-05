package org.opendiplom.printing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where every text of a blank lands: the template filled with the data and
 * laid out as FastReport lays it out, in millimetres from the top-left
 * corner of each page.
 *
 * <p>The measures were taken from the supplements CyberDiploma printed in
 * 2026 with the template of 2025, 83 graduates of seven groups. The lines of
 * a field are 16.33 pixels apart for a 15-pixel font with spacing 1.5; the
 * baseline is the ascent of Times New Roman below the top of the field; a
 * field that stretches is a whole number of pixels high, 14, 30, 47 and 63
 * for one to four lines; lines break where Times New Roman of the whole
 * points no longer fits the field, counted in whole pixels. Laid out so,
 * every line of those supplements breaks where it broke there and stands
 * within 0.1 mm of its place, besides a shift of the whole page that the
 * printer calibration takes away.
 */
public final class BlankLayout {
    /** Distance between the baselines of a field, in ems, before the spacing. */
    static final double LINE_PITCH = 0.9887;
    /** Of Times New Roman, in ems. */
    static final double ASCENT = 0.891;
    static final double DESCENT = 0.216;
    /**
     * Of a field that stretches, pixels: its height is n lines apart, plus the
     * gaps above and below, less this, rounded to whole pixels.
     */
    private static final double HEIGHT_SHORTFALL = 0.33;
    /** What a row may stick out below a column before it moves to the next, millimetres. */
    private static final double TOLERANCE = 0.01;

    private final BlankData data;
    private final Measure measure;
    private final Set<String> problems;
    private final List<Page> pages = new ArrayList<>();

    private BlankLayout(final BlankData data, final Measure measure, final Set<String> problems) {
        this.data = data;
        this.measure = measure;
        this.problems = problems;
    }

    /** Width of a text in the font of the blanks, millimetres. */
    public interface Measure {
        double width(String text, int pt, boolean bold);
    }

    /** A line of text: x of its start and its baseline, millimetres. */
    public static final class Line {
        public final String text;
        public final double x;
        public final double baseline;
        public final int pt;
        public final boolean bold;

        public Line(final String text, final double x, final double baseline, final int pt, final boolean bold) {
            this.text = text;
            this.x = x;
            this.baseline = baseline;
            this.pt = pt;
            this.bold = bold;
        }

        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%.2f %.2f %s", this.x, this.baseline, this.text);
        }
    }

    /** A printed page: one side of a blank. */
    public static final class Page {
        public final double width;
        public final double height;
        public final List<Line> lines = new ArrayList<>();

        public Page(final double width, final double height) {
            this.width = width;
            this.height = height;
        }
    }

    /**
     * The pages of a template filled with the data.
     *
     * @param problems where fields the data has not and texts that do not fit are reported
     */
    public static List<Page> of(
        final BlankTemplate template, final BlankData data, final Measure measure, final Set<String> problems
    ) {
        final BlankLayout layout = new BlankLayout(data, measure, problems);
        for (int number = 0; number < template.sheets.size(); ++number) {
            layout.sheet(template.sheets.get(number), number + 1);
        }
        return Collections.unmodifiableList(layout.pages);
    }

    private void sheet(final BlankTemplate.Sheet sheet, final int number) {
        Page page = this.page(sheet, true);
        final double top = sheet.top + sheet.headerHeight;
        final double bottom = sheet.height - sheet.bottom - sheet.footerHeight;
        int column = 0;
        double y = top;
        for (final BlankTemplate.Band band : sheet.bands) {
            for (final Map<String, Object> row : this.rows(band)) {
                final Map<String, Object> fields = new HashMap<>(this.data.fields());
                fields.putAll(row);
                final List<Placed> placed = this.fill(band, fields);
                final double height = height(band, placed);
                if (y + height > bottom + TOLERANCE && y > top) {
                    if (++column >= sheet.columns) {
                        column = 0;
                        page = this.page(sheet, false);
                        this.problems.add("Лист " + number + " шаблона: таблица не поместилась, добавлена страница");
                    }
                    y = top;
                }
                this.band(page, band, placed, sheet.left + sheet.columnPositions.get(column), y, height);
                y += height;
            }
        }
    }

    /** A new page of a sheet with its fields, header, footer and, on the first, subreports. */
    private Page page(final BlankTemplate.Sheet sheet, final boolean first) {
        final Page page = new Page(sheet.width, sheet.height);
        this.pages.add(page);
        final Map<String, Object> fields = this.data.fields();
        for (final BlankTemplate.Memo memo : sheet.memos) {
            this.memo(page, this.place(memo, fields), sheet.left, sheet.top, -1);
        }
        for (final BlankTemplate.Memo memo : sheet.header) {
            this.memo(page, this.place(memo, fields), sheet.left, sheet.top, -1);
        }
        final double footer = sheet.height - sheet.bottom - sheet.footerHeight;
        for (final BlankTemplate.Memo memo : sheet.footer) {
            this.memo(page, this.place(memo, fields), sheet.left, footer, -1);
        }
        if (first) {
            for (final BlankTemplate.Subreport subreport : sheet.subreports) {
                double y = sheet.top + subreport.y;
                for (final BlankTemplate.Band band : subreport.page.bands) {
                    for (final Map<String, Object> row : this.rows(band)) {
                        final Map<String, Object> merged = new HashMap<>(fields);
                        merged.putAll(row);
                        final List<Placed> placed = this.fill(band, merged);
                        final double height = height(band, placed);
                        this.band(page, band, placed, sheet.left + subreport.x, y, height);
                        y += height;
                    }
                }
            }
        }
        return page;
    }

    private List<Map<String, Object>> rows(final BlankTemplate.Band band) {
        if (band.dataset.isEmpty()) {
            return Collections.nCopies(Math.max(0, band.count), Collections.emptyMap());
        }
        if (!this.data.has(band.dataset)) {
            this.problems.add("Набора данных «" + band.dataset + "» нет: полоса " + band.name + " не печатается");
        }
        return this.data.rows(band.dataset);
    }

    /** A field with its text broken into lines. */
    private static final class Placed {
        final BlankTemplate.Memo memo;
        final List<String> lines;
        /** Whether a line ends its paragraph: it is not spread out by justification. */
        final List<Boolean> last;

        Placed(final BlankTemplate.Memo memo, final List<String> lines, final List<Boolean> last) {
            this.memo = memo;
            this.lines = lines;
            this.last = last;
        }

        /** As FastReport stretches a field to its text: whole pixels. */
        double height() {
            if (!this.memo.stretch) {
                return this.memo.height;
            }
            final double pitch = (LINE_PITCH * this.memo.em + this.memo.lineSpacing) * BlankTemplate.PX;
            final double pixels = this.lines.size() * pitch + 2 * this.memo.gapY * BlankTemplate.PX - HEIGHT_SHORTFALL;
            return Math.max(0, Math.round(pixels)) / BlankTemplate.PX;
        }
    }

    private List<Placed> fill(final BlankTemplate.Band band, final Map<String, Object> fields) {
        final List<Placed> placed = new ArrayList<>();
        for (final BlankTemplate.Memo memo : band.memos) {
            placed.add(this.place(memo, fields));
        }
        return placed;
    }

    private Placed place(final BlankTemplate.Memo memo, final Map<String, Object> fields) {
        final String text = new Expressions(fields, this.problems).text(memo.text);
        final List<String> lines = new ArrayList<>();
        final List<Boolean> last = new ArrayList<>();
        if (text.isEmpty()) {
            return new Placed(memo, lines, last);
        }
        // FastReport breaks lines in whole pixels of the field: 413 for a field of 413.6
        final double width = Math.floor((memo.width - 2 * memo.gapX) * BlankTemplate.PX + 1e-6) / BlankTemplate.PX;
        for (final String paragraph : text.split("\n", -1)) {
            if (!memo.wrap) {
                lines.add(paragraph);
                last.add(true);
                if (!memo.autoWidth && this.width(paragraph, memo) > width + TOLERANCE) {
                    this.problems.add("Поле " + memo.name + ": текст шире поля");
                }
                continue;
            }
            final int start = lines.size();
            this.wrap(paragraph, memo, width, lines);
            for (int line = start; line < lines.size(); ++line) {
                last.add(line == lines.size() - 1);
            }
        }
        return new Placed(memo, lines, last);
    }

    /** Breaks a paragraph at spaces, a word wider than the field by letters. */
    private void wrap(final String paragraph, final BlankTemplate.Memo memo, final double width, final List<String> lines) {
        String current = null;
        for (final String word : paragraph.split(" ", -1)) {
            final String candidate = current == null ? word : current + " " + word;
            if (current == null || this.width(candidate, memo) <= width + TOLERANCE) {
                current = candidate;
            } else {
                lines.add(current);
                current = word;
            }
            while (current.length() > 1 && this.width(current, memo) > width + TOLERANCE) {
                int fits = current.length() - 1;
                while (fits > 1 && this.width(current.substring(0, fits), memo) > width + TOLERANCE) {
                    --fits;
                }
                lines.add(current.substring(0, fits));
                current = current.substring(fits);
            }
        }
        lines.add(current == null ? "" : current);
    }

    private double width(final String text, final BlankTemplate.Memo memo) {
        return this.measure.width(text, memo.fontPt, memo.bold);
    }

    /** A stretched band is as high as its fields, plus what the script adds. */
    private static double height(final BlankTemplate.Band band, final List<Placed> placed) {
        if (!band.stretched) {
            return band.height;
        }
        double height = 0;
        for (final Placed field : placed) {
            height = Math.max(height, field.memo.y + field.height());
        }
        return height + band.growth;
    }

    private void band(
        final Page page, final BlankTemplate.Band band, final List<Placed> placed, final double x, final double y,
        final double height
    ) {
        double tallest = 0;
        for (final Placed field : placed) {
            if (field.memo.wrap) {
                tallest = Math.max(tallest, field.height());
            }
        }
        for (final Placed field : placed) {
            double top = y;
            if (band.lastLine && !field.memo.wrap && !field.lines.isEmpty()) {
                // the script sets Top := Height - Height of the field: the last line of the row
                top += Math.max(0, tallest - field.height());
            }
            this.memo(page, field, x, top, height);
        }
    }

    /**
     * Writes the lines of a field.
     *
     * @param band height of the band it stands in, or -1 for a field of the page
     */
    private void memo(final Page page, final Placed field, final double x, final double y, final double band) {
        final BlankTemplate.Memo memo = field.memo;
        final int count = field.lines.size();
        if (count == 0) {
            return;
        }
        final double pitch = LINE_PITCH * memo.em + memo.lineSpacing;
        final double text = (count - 1) * pitch + (ASCENT + DESCENT) * memo.em;
        final double height = field.height();
        double baseline = y + memo.y + memo.gapY + ASCENT * memo.em;
        if (memo.valign == BlankTemplate.Valign.CENTER) {
            baseline = y + memo.y + (height - text) / 2 + ASCENT * memo.em;
        } else if (memo.valign == BlankTemplate.Valign.BOTTOM) {
            baseline = y + memo.y + height - memo.gapY - text + ASCENT * memo.em;
        }
        if (!memo.stretch && band < 0 && memo.gapY + text > height + pitch / 2) {
            this.problems.add("Поле " + memo.name + ": текст не помещается по высоте");
        }
        final double left = x + memo.x + memo.gapX;
        final double width = memo.width - 2 * memo.gapX;
        for (int number = 0; number < count; ++number) {
            final String line = field.lines.get(number);
            final double lineWidth = this.width(line, memo);
            final double at;
            if (memo.align == BlankTemplate.Align.CENTER) {
                at = x + memo.x + (memo.width - lineWidth) / 2;
            } else if (memo.align == BlankTemplate.Align.RIGHT) {
                at = left + width - lineWidth;
            } else {
                at = left;
            }
            final double lineBaseline = baseline + number * pitch;
            if (memo.align == BlankTemplate.Align.BLOCK && !field.last.get(number) && line.contains(" ")) {
                this.spread(page, memo, line, left, width, lineBaseline);
            } else if (!line.isEmpty()) {
                page.lines.add(new Line(line, at, lineBaseline, memo.fontPt, memo.bold));
            }
        }
    }

    /** A justified line: the words with the spare width shared between them. */
    private void spread(
        final Page page, final BlankTemplate.Memo memo, final String line, final double left, final double width,
        final double baseline
    ) {
        final String[] words = line.strip().split(" +");
        double used = 0;
        for (final String word : words) {
            used += this.width(word, memo);
        }
        final double gap = words.length > 1 ? (width - used) / (words.length - 1) : 0;
        double at = left;
        for (final String word : words) {
            page.lines.add(new Line(word, at, baseline, memo.fontPt, memo.bold));
            at += this.width(word, memo) + gap;
        }
    }
}
