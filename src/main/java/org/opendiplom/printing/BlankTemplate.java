package org.opendiplom.printing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A blank as a FastReport template describes it, in millimetres (ADR-0003):
 * sheets with fields, and bands of data that flow down the columns of a
 * sheet, such as the table of disciplines of a supplement.
 *
 * <p>FastReport keeps positions in pixels at 96 dpi, relative to the page
 * margins, and stacks the bands of a page: the page header at the top, the
 * data bands in the order of their position, the page footer at the bottom.
 *
 * <p>The scripts of the templates are not run. Two things they do are
 * known instead. They set the line spacing of the table rows
 * («LineSpacing := 1.5»), which is taken from the script. And a band with an
 * {@code OnAfterCalcHeight} handler grows by 2 pixels and puts the fields
 * that do not wrap, the credits and the grade, on the last line of its
 * row: {@link Band#lastLine}.
 */
public final class BlankTemplate {
    /** FastReport pixels in a millimetre: 96 dpi. */
    static final double PX = 96 / 25.4;
    /** A field of a data set: {@code Студент."Фамилия"}. */
    private static final Pattern FIELD = Pattern.compile("([^\\[\\]<>\"()=,']+?)\\.\"([^\"]+)\"");
    private static final int BOLD = 1;
    private static final double GAP_X = 2;
    private static final double GAP_Y = 1;
    /** What the CyberDiploma scripts add to a row with an {@code OnAfterCalcHeight} handler, pixels. */
    private static final double ROW_GROWTH = 2;
    private static final Pattern WITH = Pattern.compile("with\\s+(\\w+)\\s+do\\s+begin(.*?)\\bend;", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACING = Pattern.compile("(?:(\\w+)\\.)?LineSpacing\\s*:=\\s*([\\d.]+|LineSpacing)\\s*;", Pattern.CASE_INSENSITIVE);

    /** The sheets that print, in order: the subreport pages print inside them. */
    public final List<Sheet> sheets;

    private BlankTemplate(final List<Sheet> sheets) {
        this.sheets = Collections.unmodifiableList(sheets);
    }

    /** How a text sits in its field across. */
    public enum Align { LEFT, CENTER, RIGHT, BLOCK }

    /** How a text sits in its field from top to bottom. */
    public enum Valign { TOP, CENTER, BOTTOM }

    /** A text field, its position relative to what holds it. */
    public static final class Memo {
        public final String name;
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        /** The em of the font, millimetres: FastReport measures lines by it. */
        public final double em;
        /** Whole points, as Delphi rounds the size of a font in pixels. */
        public final int fontPt;
        public final boolean bold;
        public final Align align;
        public final Valign valign;
        public final boolean wrap;
        /** Grows to its text: a text wider than the field is not a fault. */
        public final boolean autoWidth;
        public final boolean stretch;
        /** Between lines, millimetres. */
        public final double lineSpacing;
        public final double gapX;
        public final double gapY;
        /** With FastReport expressions in brackets. */
        public final String text;

        Memo(final Fr3.Node node, final Map<String, Double> spacing) {
            this.name = node.text("Name", "");
            this.x = node.number("Left", 0) / PX;
            this.y = node.number("Top", 0) / PX;
            this.width = node.number("Width", 0) / PX;
            this.height = node.number("Height", 0) / PX;
            // a negative height is the em size in pixels; 72 points in 96 pixels
            final double pixels = Math.abs(node.number("Font.Height", -13));
            this.em = pixels / PX;
            this.fontPt = (int) Math.round(pixels * 72 / 96);
            this.bold = ((int) node.number("Font.Style", 0) & BOLD) != 0;
            final String align = node.text("HAlign", "haLeft");
            this.align = "haCenter".equals(align) ? Align.CENTER : "haRight".equals(align) ? Align.RIGHT
                : "haBlock".equals(align) ? Align.BLOCK : Align.LEFT;
            final String valign = node.text("VAlign", "vaTop");
            this.valign = "vaCenter".equals(valign) ? Valign.CENTER : "vaBottom".equals(valign) ? Valign.BOTTOM
                : Valign.TOP;
            this.wrap = !"False".equals(node.text("WordWrap", "True"));
            this.autoWidth = "True".equals(node.text("AutoWidth", "False"));
            this.stretch = node.text("StretchMode", "smDontStretch").startsWith("smActual")
                || node.text("StretchMode", "").startsWith("smMax");
            this.lineSpacing = spacing.getOrDefault(this.name, node.number("LineSpacing", 2)) / PX;
            this.gapX = node.number("GapX", GAP_X) / PX;
            this.gapY = node.number("GapY", GAP_Y) / PX;
            this.text = node.text("Text", "");
        }

        /** The data sets its text reads. */
        Set<String> datasets() {
            final Set<String> found = new LinkedHashSet<>();
            final Matcher field = FIELD.matcher(this.text);
            while (field.find()) {
                found.add(field.group(1).strip());
            }
            return found;
        }
    }

    /** A band: printed a given number of times, or once a row of its data set. */
    public static final class Band {
        public final String name;
        /** The data set whose rows it prints, empty for a band printed {@link #count} times. */
        public final String dataset;
        /** How many times a band without a data set prints; 0 prints it never, as FastReport does. */
        public final int count;
        public final double height;
        /** Takes the height of its fields rather than its own. */
        public final boolean stretched;
        /** Added to the height of each row, millimetres. */
        public final double growth;
        /** The fields that do not wrap stand on the last line of the row. */
        public final boolean lastLine;
        public final List<Memo> memos;

        Band(final Fr3.Node node, final String dataset, final List<Memo> memos) {
            this.name = node.text("Name", "");
            this.dataset = dataset;
            this.count = (int) node.number("RowCount", 0);
            this.height = node.number("Height", 0) / PX;
            this.stretched = "True".equals(node.text("Stretched", "False"));
            final boolean script = !node.text("OnAfterCalcHeight", "").isEmpty();
            this.growth = script ? ROW_GROWTH / PX : 0;
            this.lastLine = script;
            this.memos = Collections.unmodifiableList(memos);
        }
    }

    /** A subreport: the bands of another page printed in a rectangle of this one. */
    public static final class Subreport {
        public final double x;
        public final double y;
        public final double width;
        public final double height;
        public final Sheet page;

        Subreport(final Fr3.Node node, final Sheet page) {
            this.x = node.number("Left", 0) / PX;
            this.y = node.number("Top", 0) / PX;
            this.width = node.number("Width", 0) / PX;
            this.height = node.number("Height", 0) / PX;
            this.page = page;
        }
    }

    /** A report page: one side of a sheet. */
    public static final class Sheet {
        public final String name;
        public final double width;
        public final double height;
        public final double left;
        public final double top;
        public final double bottom;
        public final int columns;
        /** Where each column starts from the left margin, millimetres. */
        public final List<Double> columnPositions;
        /** Fields placed on the page itself. */
        public final List<Memo> memos = new ArrayList<>();
        public final List<Band> bands = new ArrayList<>();
        public final List<Subreport> subreports = new ArrayList<>();
        public double headerHeight;
        public double footerHeight;
        public final List<Memo> footer = new ArrayList<>();
        public final List<Memo> header = new ArrayList<>();

        Sheet(final Fr3.Node node) {
            this.name = node.text("Name", "");
            final double paperWidth = node.number("PaperWidth", 210);
            final double paperHeight = node.number("PaperHeight", 297);
            final boolean landscape = "poLandscape".equals(node.text("Orientation", ""));
            this.width = landscape ? Math.max(paperWidth, paperHeight) : Math.min(paperWidth, paperHeight);
            this.height = landscape ? Math.min(paperWidth, paperHeight) : Math.max(paperWidth, paperHeight);
            this.left = node.number("LeftMargin", 0);
            this.top = node.number("TopMargin", 0);
            this.bottom = node.number("BottomMargin", 0);
            this.columns = Math.max(1, (int) node.number("Columns", 1));
            final double columnWidth = node.number("ColumnWidth", this.width - this.left);
            // the positions may differ from the widths: 0 and 209 for columns 210 wide
            final String[] given = node.text("ColumnPositions.Text", "").strip().split("\\s+");
            final List<Double> positions = new ArrayList<>();
            for (int column = 0; column < this.columns; ++column) {
                double position = column * columnWidth;
                if (column < given.length) {
                    try {
                        position = Double.parseDouble(given[column].replace(',', '.'));
                    } catch (final NumberFormatException error) {
                        position = column * columnWidth;
                    }
                }
                positions.add(position);
            }
            this.columnPositions = Collections.unmodifiableList(positions);
        }

        /** The data bands, in the order FastReport prints them. */
        public List<Band> bands() {
            return Collections.unmodifiableList(this.bands);
        }
    }

    /**
     * The template of a FastReport file.
     *
     * @throws IOException when it is not a FastReport template or has no page
     */
    public static BlankTemplate of(final byte[] content) throws IOException {
        final Fr3.Node report = Fr3.read(content);
        final Map<String, Double> spacing = spacing(report.text("ScriptText.Text", ""));
        final List<Fr3.Node> pages = report.all("TfrxReportPage");
        final Set<String> nested = new HashSet<>();
        for (final Fr3.Node page : pages) {
            for (final Fr3.Node child : page.children) {
                if ("TfrxSubreport".equals(child.tag)) {
                    nested.add(child.text("Page", ""));
                }
            }
        }
        final Map<String, Sheet> read = new LinkedHashMap<>();
        for (final Fr3.Node page : pages) {
            read.put(page.text("Name", ""), sheet(page, spacing));
        }
        final List<Sheet> sheets = new ArrayList<>();
        for (final Fr3.Node page : pages) {
            final Sheet sheet = read.get(page.text("Name", ""));
            for (final Fr3.Node child : page.children) {
                if ("TfrxSubreport".equals(child.tag) && read.containsKey(child.text("Page", ""))) {
                    sheet.subreports.add(new Subreport(child, read.get(child.text("Page", ""))));
                }
            }
            if (!nested.contains(sheet.name)) {
                sheets.add(sheet);
            }
        }
        if (sheets.isEmpty()) {
            throw new IOException("В шаблоне нет ни одной страницы для печати");
        }
        return new BlankTemplate(sheets);
    }

    /** The data sets whose rows the bands print, of all sheets and subreports. */
    public Set<String> datasets() {
        final Set<String> datasets = new LinkedHashSet<>();
        final Set<Sheet> seen = new HashSet<>();
        final List<Sheet> next = new ArrayList<>(this.sheets);
        while (!next.isEmpty()) {
            final Sheet sheet = next.remove(0);
            if (seen.add(sheet)) {
                for (final Band band : sheet.bands) {
                    if (!band.dataset.isEmpty()) {
                        datasets.add(band.dataset);
                    }
                }
                for (final Subreport subreport : sheet.subreports) {
                    next.add(subreport.page);
                }
            }
        }
        return datasets;
    }

    /** Every text of the template, for checking which fields it asks. */
    public List<String> texts() {
        final List<String> texts = new ArrayList<>();
        final Set<Sheet> seen = new HashSet<>();
        for (final Sheet sheet : this.sheets) {
            texts(sheet, texts, seen);
        }
        return texts;
    }

    private static void texts(final Sheet sheet, final List<String> texts, final Set<Sheet> seen) {
        if (!seen.add(sheet)) {
            return;
        }
        final List<Memo> all = new ArrayList<>(sheet.memos);
        all.addAll(sheet.header);
        all.addAll(sheet.footer);
        for (final Band band : sheet.bands) {
            all.addAll(band.memos);
        }
        for (final Memo memo : all) {
            if (!memo.text.isEmpty()) {
                texts.add(memo.text);
            }
        }
        for (final Subreport subreport : sheet.subreports) {
            texts(subreport.page, texts, seen);
        }
    }

    /** Line spacing the script gives to fields, pixels by field name. */
    static Map<String, Double> spacing(final String script) {
        final Map<String, Double> spacing = new HashMap<>();
        final Matcher block = WITH.matcher(script);
        while (block.find()) {
            Double own = null;
            final Matcher assignment = SPACING.matcher(block.group(2));
            while (assignment.find()) {
                final String target = assignment.group(1) == null ? block.group(1) : assignment.group(1);
                final Double value = "LineSpacing".equalsIgnoreCase(assignment.group(2)) ? own
                    : Double.valueOf(assignment.group(2));
                if (value != null) {
                    spacing.put(target, value);
                    if (assignment.group(1) == null) {
                        own = value;
                    }
                }
            }
        }
        return spacing;
    }

    private static Sheet sheet(final Fr3.Node page, final Map<String, Double> spacing) {
        final Sheet sheet = new Sheet(page);
        final List<Fr3.Node> data = new ArrayList<>();
        for (final Fr3.Node child : page.children) {
            switch (child.tag) {
                case "TfrxMemoView":
                    sheet.memos.add(new Memo(child, spacing));
                    break;
                case "TfrxPageHeader":
                    sheet.headerHeight = child.number("Height", 0) / PX;
                    sheet.header.addAll(memos(child, spacing));
                    break;
                case "TfrxPageFooter":
                    sheet.footerHeight = child.number("Height", 0) / PX;
                    sheet.footer.addAll(memos(child, spacing));
                    break;
                case "TfrxMasterData":
                case "TfrxDetailData":
                case "TfrxHeader":
                case "TfrxFooter":
                    data.add(child);
                    break;
                default:
                    break;
            }
        }
        data.sort(Comparator.comparingDouble(node -> node.number("Top", 0)));
        for (final Fr3.Node band : data) {
            final List<Memo> memos = memos(band, spacing);
            final Set<String> datasets = new LinkedHashSet<>();
            for (final Memo memo : memos) {
                datasets.addAll(memo.datasets());
            }
            // a band with a row count prints that many times without data; the data
            // set names of the files are unreadable, so the fields tell the data set
            final boolean counted = band.number("RowCount", 0) > 0 || datasets.isEmpty();
            sheet.bands.add(new Band(band, counted ? "" : datasets.iterator().next(), memos));
        }
        return sheet;
    }

    private static List<Memo> memos(final Fr3.Node band, final Map<String, Double> spacing) {
        final List<Memo> memos = new ArrayList<>();
        for (final Fr3.Node child : band.children) {
            if ("TfrxMemoView".equals(child.tag)) {
                memos.add(new Memo(child, spacing));
            }
        }
        return memos;
    }
}
