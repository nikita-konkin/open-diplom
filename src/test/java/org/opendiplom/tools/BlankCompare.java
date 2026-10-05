package org.opendiplom.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.opendiplom.printing.BlankData;
import org.opendiplom.printing.BlankLayout;
import org.opendiplom.printing.BlankPdf;
import org.opendiplom.printing.BlankTemplate;
import org.opendiplom.printing.Calibration;

/**
 * Lays out again, with a template, the supplements CyberDiploma printed, and
 * tells how far each text of ours stands from the printed one. The rows of
 * the table and of the additional information are taken from the printed
 * PDF, so only the layout is compared: where lines break, where rows,
 * columns and pages start. Not part of the program.
 *
 * <p>Usage: {@code BlankCompare <template.fr3> <font.ttf> <printed.pdf>...}.
 * It prints counts and millimetres, never a text: the PDFs hold personal
 * data. A text not found is mostly a line break inside a thesis topic of the
 * data, which CyberDiploma keeps and the registry does not.
 */
public final class BlankCompare {
    /**
     * Below it, a step down from one line to the next starts a new row: rows
     * are 4.23 mm a line, lines of a field 4.32 mm apart, for the 15-pixel
     * font of the CyberDiploma templates.
     */
    private static final double ROW_STEP = Double.parseDouble(System.getProperty("row-step", "4.25"));
    /** How far the printer of CyberDiploma may have shifted the page, millimetres. */
    private static final double SHIFT = 0.6;
    private static final double MM_PER_POINT = 25.4 / 72;
    private static final Pattern FIELD = Pattern.compile("([А-Яа-яЁё][А-Яа-яЁё ]*?)\\.\"([^\"]+)\"");

    private BlankCompare() {
    }

    /** A text of the printed PDF: x of its first letter, its baseline and right end, millimetres. */
    private static final class Chunk {
        final double x;
        final double y;
        final double right;
        final String text;

        Chunk(final double x, final double y, final double right, final String text) {
            this.x = x;
            this.y = y;
            this.right = right;
            this.text = text.strip();
        }
    }

    public static void main(final String... args) throws Exception {
        System.setProperty(
            "log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory"
        );
        final BlankTemplate template = BlankTemplate.of(Files.readAllBytes(Paths.get(args[0])));
        final Path font = Paths.get(args[1]);
        final double[] total = new double[5];
        for (int file = 2; file < args.length; ++file) {
            try (PDDocument printed = Loader.loadPDF(Paths.get(args[file]).toFile());
                 BlankPdf pdf = new BlankPdf(font, null, Calibration.NONE)) {
                final int sides = template.sheets.size();
                for (int first = 0; first + sides <= printed.getNumberOfPages(); first += sides) {
                    final double[] result = compare(template, printed, first, pdf.measure());
                    System.out.printf(Locale.ROOT, "%s, pages %d-%d: texts %d, not found %d; x off by %.2f on average, "
                            + "%.2f at most; baseline off by %.2f at most, mm%n", Paths.get(args[file]).getFileName(),
                        first + 1, first + sides, (int) result[0], (int) result[1], result[2] / Math.max(1, result[0]
                            - result[1]), result[3], result[4]);
                    total[0] += result[0];
                    total[1] += result[1];
                    total[3] = Math.max(total[3], result[3]);
                    total[4] = Math.max(total[4], result[4]);
                }
            }
        }
        System.out.printf(Locale.ROOT, "in all: texts %d, not found %d; x off by %.2f at most, baseline by %.2f at most, mm%n",
            (int) total[0], (int) total[1], total[3], total[4]);
    }

    /** Texts, not found, sum of x deviations, largest x and baseline deviations. */
    private static double[] compare(
        final BlankTemplate template, final PDDocument printed, final int first, final BlankLayout.Measure measure
    ) throws IOException {
        final Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        final List<List<Chunk>> compared = new ArrayList<>();
        for (int sheet = 0; sheet < template.sheets.size(); ++sheet) {
            compared.add(new ArrayList<>());
        }
        for (int number = 0; number < template.sheets.size(); ++number) {
            final BlankTemplate.Sheet sheet = template.sheets.get(number);
            final List<Chunk> page = chunks(printed, first + number);
            for (final BlankTemplate.Band band : sheet.bands) {
                if (band.dataset.isEmpty()) {
                    continue;
                }
                final double top = sheet.top + sheet.headerHeight;
                final double bottom = sheet.height - sheet.bottom - sheet.footerHeight;
                for (int column = 0; column < sheet.columns; ++column) {
                    final double left = sheet.left + sheet.columnPositions.get(column);
                    final double right = column + 1 < sheet.columns
                        ? sheet.left + sheet.columnPositions.get(column + 1) : sheet.width;
                    final List<Chunk> area = new ArrayList<>();
                    for (final Chunk chunk : page) {
                        if (chunk.x >= left - SHIFT && chunk.x < right - SHIFT && chunk.y > top && chunk.y < bottom) {
                            area.add(chunk);
                        }
                    }
                    rows.computeIfAbsent(band.dataset, any -> new ArrayList<>()).addAll(rows(band, left, area));
                    compared.get(number).addAll(area);
                }
            }
            for (final BlankTemplate.Subreport subreport : sheet.subreports) {
                for (final BlankTemplate.Band band : subreport.page.bands) {
                    final List<Chunk> area = new ArrayList<>();
                    for (final Chunk chunk : page) {
                        if (chunk.x >= sheet.left + subreport.x - SHIFT && chunk.x < sheet.left + subreport.x + subreport.width
                            && chunk.y > sheet.top + subreport.y && chunk.y < sheet.top + subreport.y + subreport.height) {
                            area.add(chunk);
                        }
                    }
                    rows.computeIfAbsent(band.dataset, any -> new ArrayList<>())
                        .addAll(rows(band, sheet.left + subreport.x, area));
                    compared.get(number).addAll(area);
                }
            }
        }
        final Map<String, Object> fields = new HashMap<>();
        for (final String text : template.texts()) {
            final Matcher field = FIELD.matcher(text);
            while (field.find()) {
                fields.putIfAbsent(field.group(1).strip() + ".\"" + field.group(2) + '"', "");
            }
        }
        final List<BlankLayout.Page> pages = BlankLayout.of(
            template, BlankData.of(fields, rows), measure, new LinkedHashSet<>()
        );
        final double[] result = new double[5];
        for (int number = 0; number < compared.size(); ++number) {
            final List<BlankLayout.Line> laid = new ArrayList<>(pages.get(number).lines);
            for (final Chunk chunk : compared.get(number)) {
                ++result[0];
                BlankLayout.Line best = null;
                for (final BlankLayout.Line line : laid) {
                    if (line.text.strip().equals(chunk.text) && (best == null || distance(line, chunk) < distance(best, chunk))) {
                        best = line;
                    }
                }
                if (best == null || distance(best, chunk) > 3) {
                    ++result[1];
                    continue;
                }
                laid.remove(best);
                result[2] += best.x - chunk.x;
                result[3] = Math.max(result[3], Math.abs(best.x - chunk.x));
                result[4] = Math.max(result[4], Math.abs(best.baseline - chunk.y));
            }
        }
        return result;
    }

    private static double distance(final BlankLayout.Line line, final Chunk chunk) {
        return Math.abs(line.baseline - chunk.y) + Math.abs(line.x - chunk.x);
    }

    /**
     * The rows of a band from the printed texts of one column: the texts at
     * the first field are the lines of the rows; a row ends where another
     * field has a text beside it, or where the next line steps down by a row.
     */
    private static List<Map<String, Object>> rows(final BlankTemplate.Band band, final double left, final List<Chunk> area) {
        final List<BlankTemplate.Memo> memos = new ArrayList<>(band.memos);
        memos.sort(Comparator.comparingDouble(memo -> memo.x));
        final BlankTemplate.Memo first = memos.get(0);
        final List<Chunk> lines = new ArrayList<>();
        final List<Chunk> beside = new ArrayList<>();
        for (final Chunk chunk : area) {
            (Math.abs(chunk.x - (left + first.x + first.gapX)) < SHIFT ? lines : beside).add(chunk);
        }
        lines.sort(Comparator.comparingDouble(chunk -> chunk.y));
        final List<List<Chunk>> grouped = new ArrayList<>();
        Chunk previous = null;
        boolean ended = true;
        for (final Chunk line : lines) {
            if (ended || line.y - previous.y < ROW_STEP) {
                grouped.add(new ArrayList<>());
            }
            grouped.get(grouped.size() - 1).add(line);
            previous = line;
            ended = beside.stream().anyMatch(chunk -> Math.abs(chunk.y - line.y) < 0.3);
        }
        final List<Map<String, Object>> rows = new ArrayList<>();
        for (final List<Chunk> row : grouped) {
            final Map<String, Object> fields = new LinkedHashMap<>();
            final List<String> texts = new ArrayList<>();
            for (final Chunk line : row) {
                texts.add(line.text);
            }
            for (final BlankTemplate.Memo memo : memos) {
                fields.put(field(memo), "");
            }
            fields.put(field(first), String.join(" ", texts));
            final double top = row.get(0).y - 0.2;
            final double bottom = row.get(row.size() - 1).y + 0.2;
            for (final Chunk chunk : beside) {
                if (chunk.y >= top && chunk.y <= bottom) {
                    BlankTemplate.Memo nearest = null;
                    for (final BlankTemplate.Memo memo : memos.subList(1, memos.size())) {
                        if (nearest == null || Math.abs(centre(memo, left) - (chunk.x + chunk.right) / 2)
                            < Math.abs(centre(nearest, left) - (chunk.x + chunk.right) / 2)) {
                            nearest = memo;
                        }
                    }
                    if (nearest != null) {
                        fields.put(field(nearest), chunk.text);
                    }
                }
            }
            rows.add(fields);
        }
        return rows;
    }

    private static double centre(final BlankTemplate.Memo memo, final double left) {
        return left + memo.x + memo.width / 2;
    }

    /** The field a memo prints: {@code [Набор."Поле"]}. */
    private static String field(final BlankTemplate.Memo memo) {
        final Matcher field = FIELD.matcher(memo.text);
        return field.find() ? field.group(1).strip() + ".\"" + field.group(2) + '"' : memo.name;
    }

    private static List<Chunk> chunks(final PDDocument document, final int page) throws IOException {
        final List<Chunk> chunks = new ArrayList<>();
        final PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(final String text, final List<TextPosition> positions) {
                final TextPosition first = positions.get(0);
                final TextPosition last = positions.get(positions.size() - 1);
                chunks.add(new Chunk(first.getXDirAdj() * MM_PER_POINT, first.getYDirAdj() * MM_PER_POINT,
                    (last.getXDirAdj() + last.getWidthDirAdj()) * MM_PER_POINT, text));
            }
        };
        stripper.setSortByPosition(true);
        stripper.setStartPage(page + 1);
        stripper.setEndPage(page + 1);
        stripper.getText(document);
        return Collections.unmodifiableList(chunks);
    }
}
