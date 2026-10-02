package org.opendiplom.plans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.plans.PlanItem.Kind;
import org.opendiplom.plans.PlanItem.Section;

/**
 * Rows of a plan put in place.
 *
 * <p>A row belongs to the row with the longest index that starts its own
 * («Б.1.1» holds «Б.1.1.1»); a row whose index starts with no other belongs to
 * the block or heading above it. The block tells a discipline from a practice
 * and the attestation; facultatives follow their heading or have «ФТД» in the
 * index.
 *
 * <p>«Планы» lists a practice as its kind with its types under it:
 * «Производственная практика» → «Преддипломная практика». The 2026
 * supplements print them as «Производственная практика (преддипломная
 * практика)», and so does {@link PlanItem#printed()}. A kind whose types are
 * one practice in parts, «Технологическая практика» and «Технологическая
 * практика (рассредоточенная)», is printed the same way by the shortest type:
 * a statement may grade the parts together.
 */
public final class PlanStructure {
    private static final Pattern BLOCK = Pattern.compile("^блок (\\d+)");
    private static final String PROGRAM = "объем образовательной программы";
    private static final List<String> PARTS = Arrays.asList(
        "обязательная часть", "часть формируемая", "базовая часть", "вариативная часть"
    );
    private static final String FACULTATIVE = "факультатив";
    private static final String FACULTATIVE_INDEX = "ФТД";
    private static final String ALTERNATIVE = " / ";
    /** «Элективная дисциплина 1 (…)»: the brackets hold the choice even when it is one discipline. */
    private static final Pattern ELECTIVE = Pattern.compile("^(элективн|дисциплин\\S* по выбору)");

    private final List<PlanItem> items;

    private PlanStructure(final List<PlanItem> items) {
        this.items = Collections.unmodifiableList(items);
    }

    public static PlanStructure of(final List<PlanRow> rows) {
        final int size = rows.size();
        final Kind[] kinds = new Kind[size];
        final Section[] sections = new Section[size];
        final int[] parents = new int[size];
        Section section = Section.NONE;
        int heading = -1;
        for (int position = 0; position < size; ++position) {
            final PlanRow row = rows.get(position);
            final String key = PlanRow.key(row.name());
            parents[position] = -1;
            if (key.contains(PROGRAM)) {
                kinds[position] = Kind.TOTAL;
                section = Section.NONE;
                heading = -1;
            } else if (row.index().isEmpty()) {
                final Matcher block = BLOCK.matcher(key);
                kinds[position] = block.find() ? Kind.BLOCK : Kind.HEADING;
                if (kinds[position] == Kind.BLOCK) {
                    section = block(Integer.parseInt(block.group(1)));
                } else if (key.startsWith(FACULTATIVE)) {
                    section = Section.FACULTATIVES;
                }
                heading = position;
            } else {
                kinds[position] = PARTS.stream().anyMatch(key::startsWith) ? Kind.PART : Kind.ELEMENT;
                if (row.index().toUpperCase(Locale.ROOT).startsWith(FACULTATIVE_INDEX)) {
                    section = Section.FACULTATIVES;
                }
                final int parent = parent(rows, position);
                parents[position] = parent < 0 ? heading : parent;
            }
            sections[position] = kinds[position] == Kind.TOTAL ? Section.NONE : section;
        }
        final boolean[] parentOf = new boolean[size];
        for (final int parent : parents) {
            if (parent >= 0) {
                parentOf[parent] = true;
            }
        }
        final List<PlanItem> items = new ArrayList<>(size);
        for (int position = 0; position < size; ++position) {
            final PlanRow row = rows.get(position);
            final boolean leaf = kinds[position] == Kind.ELEMENT && !parentOf[position];
            final int parent = parents[position];
            String printed = row.name();
            if (leaf && sections[position] == Section.PRACTICES && parent >= 0 && kinds[parent] == Kind.ELEMENT) {
                printed = practice(rows.get(parent).name(), row.name());
            } else if (kinds[position] == Kind.ELEMENT && !leaf && sections[position] == Section.PRACTICES) {
                final String type = common(rows, parents, position);
                printed = type == null ? printed : practice(row.name(), type);
            }
            items.add(new PlanItem(
                position, row, kinds[position], sections[position], parent, leaf, printed,
                kinds[position] == Kind.ELEMENT ? alternatives(row.name()) : Collections.emptyList()
            ));
        }
        return new PlanStructure(items);
    }

    /** All rows in the order printed. */
    public List<PlanItem> items() {
        return this.items;
    }

    /** Rows directly under a row. */
    public List<PlanItem> children(final PlanItem parent) {
        final List<PlanItem> children = new ArrayList<>();
        for (final PlanItem item : this.items) {
            if (item.parent() == parent.position()) {
                children.add(item);
            }
        }
        return children;
    }

    /** Elements with nothing under them, facultatives included. */
    public List<PlanItem> leaves() {
        final List<PlanItem> leaves = new ArrayList<>();
        for (final PlanItem item : this.items) {
            if (item.leaf()) {
                leaves.add(item);
            }
        }
        return leaves;
    }

    /** Blocks 1–3. */
    public List<PlanItem> blocks() {
        final List<PlanItem> blocks = new ArrayList<>();
        for (final PlanItem item : this.items) {
            if (item.kind() == Kind.BLOCK && item.section() != Section.NONE) {
                blocks.add(item);
            }
        }
        return blocks;
    }

    /** The row «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ», or {@code null}. */
    public PlanItem total() {
        for (final PlanItem item : this.items) {
            if (item.kind() == Kind.TOTAL) {
                return item;
            }
        }
        return null;
    }

    private static Section block(final int number) {
        switch (number) {
            case 1:
                return Section.DISCIPLINES;
            case 2:
                return Section.PRACTICES;
            case 3:
                return Section.ATTESTATION;
            default:
                return Section.NONE;
        }
    }

    /**
     * The row above with the longest index that starts this row's index and a
     * dot; of equal ones, the nearest.
     */
    private static int parent(final List<PlanRow> rows, final int position) {
        final String index = rows.get(position).index();
        int found = -1;
        for (int above = position - 1; above >= 0; --above) {
            final String candidate = rows.get(above).index();
            if (!candidate.isEmpty() && index.startsWith(candidate + ".")
                && (found < 0 || candidate.length() > rows.get(found).index().length())) {
                found = above;
            }
        }
        return found;
    }

    /**
     * The name of the type all rows under a row share: the shortest name the
     * names of the others start with, {@code null} when there is none.
     */
    private static String common(final List<PlanRow> rows, final int[] parents, final int position) {
        final List<String> names = new ArrayList<>();
        for (int child = 0; child < rows.size(); ++child) {
            if (parents[child] == position) {
                names.add(rows.get(child).name());
            }
        }
        String shortest = null;
        for (final String name : names) {
            if (shortest == null || PlanRow.key(name).length() < PlanRow.key(shortest).length()) {
                shortest = name;
            }
        }
        for (final String name : names) {
            if (shortest == null || !PlanRow.key(name).startsWith(PlanRow.key(shortest))) {
                return null;
            }
        }
        return shortest;
    }

    /** «Вид (тип)», unless the type already names the kind: «Учебная практика (ознакомительная)». */
    public static String practice(final String kind, final String type) {
        if (PlanRow.key(type).startsWith(PlanRow.key(kind))) {
            return type;
        }
        return kind + " (" + lowerFirst(type) + ")";
    }

    /** «Преддипломная» → «преддипломная»; «НИР» stays. */
    private static String lowerFirst(final String text) {
        if (text.length() > 1 && Character.isUpperCase(text.charAt(0)) && Character.isLowerCase(text.charAt(1))) {
            return Character.toLowerCase(text.charAt(0)) + text.substring(1);
        }
        return text;
    }

    /**
     * Disciplines of an elective: «Элективная дисциплина 1 (A / B)» → A, B.
     * The brackets are the outer ones holding « / »; brackets inside a name stay.
     * An elective whose plan lists one discipline, «Элективная дисциплина 1 (A)»,
     * has that one: the supplement prints A.
     */
    static List<String> alternatives(final String name) {
        int depth = 0;
        int open = -1;
        for (int at = 0; at < name.length(); ++at) {
            final char symbol = name.charAt(at);
            if (symbol == '(') {
                if (depth == 0) {
                    open = at;
                }
                ++depth;
            } else if (symbol == ')' && depth > 0) {
                --depth;
                if (depth == 0) {
                    final List<String> found = split(name.substring(open + 1, at));
                    if (found.size() > 1 || ELECTIVE.matcher(PlanRow.key(name.substring(0, open))).find()) {
                        return found;
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /** Parts of a text at « / » outside brackets. */
    private static List<String> split(final String text) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int at = 0; at < text.length(); ++at) {
            final char symbol = text.charAt(at);
            if (symbol == '(') {
                ++depth;
            } else if (symbol == ')') {
                --depth;
            } else if (depth == 0 && text.startsWith(ALTERNATIVE, at)) {
                parts.add(text.substring(start, at).strip());
                start = at + ALTERNATIVE.length();
            }
        }
        parts.add(text.substring(start).strip());
        return parts;
    }
}
