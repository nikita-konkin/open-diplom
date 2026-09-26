package org.opendiplom.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/**
 * Volumes a supplement prints from the totals of the plan: the program
 * («ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»), practices («Блок 2»), the state final
 * attestation («Блок 3») and contact hours (the total of «Контактная работа»).
 *
 * <p>Checked against the 2026 XML files and printed supplements: all four
 * agreed with the plans for the three full-time programs, and the values typed
 * by hand did not (B-34).
 */
public final class PlanTotals {
    private static final Pattern BLOCK = Pattern.compile("^блок ([123])\\b");
    private static final String PROGRAM = "объем образовательной программы";
    private static final int HOURS = 5;
    private static final int CONTACT = 3;

    private final Double program;
    private final Double practices;
    private final Double attestation;
    private final Double contact;
    private final List<String> problems;

    private PlanTotals(
        final Double program, final Double practices, final Double attestation, final Double contact,
        final List<String> problems
    ) {
        this.program = program;
        this.practices = practices;
        this.attestation = attestation;
        this.contact = contact;
        this.problems = Collections.unmodifiableList(problems);
    }

    static PlanTotals of(final List<PlanRow> rows) {
        final Double[] blocks = new Double[4];
        PlanRow total = null;
        for (final PlanRow row : rows) {
            final String label = Cells.collapse(row.name.toLowerCase(Locale.ROOT).replace('ё', 'е'));
            final Matcher block = BLOCK.matcher(label);
            if (block.find()) {
                final int number = Integer.parseInt(block.group(1));
                blocks[number] = blocks[number] == null ? row.credits : blocks[number];
            }
            if (total == null && label.contains(PROGRAM)) {
                total = row;
            }
        }
        final List<String> problems = new ArrayList<>();
        final boolean blocked = blocks[1] != null && blocks[2] != null && blocks[3] != null;
        final Double sum = blocked ? blocks[1] + blocks[2] + blocks[3] : null;
        Double program = total == null ? null : total.credits;
        if (program != null && sum != null && !program.equals(sum)) {
            problems.add(
                "Объём программы " + number(program) + " з.е. не равен сумме блоков " + number(sum)
                    + " з.е.: проверьте учебный план"
            );
        }
        program = program == null ? sum : program;
        if (program == null) {
            problems.add("В плане не найден объём программы: нет строки «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ» "
                + "и итогов трёх блоков");
        }
        if (blocks[2] == null) {
            problems.add("В плане не найден итог «Блок 2. Практика»");
        }
        if (blocks[3] == null) {
            problems.add("В плане не найден итог «Блок 3. Государственная итоговая аттестация»");
        }
        return new PlanTotals(program, blocks[2], blocks[3], contact(total, problems), problems);
    }

    /**
     * Contact hours of the total row, when its hours add up: всего = экзамены
     * + учебные занятия, учебные занятия = контактная + самостоятельная.
     */
    private static Double contact(final PlanRow total, final List<String> problems) {
        if (total == null || total.hours.size() < HOURS || total.hours.subList(0, HOURS).contains(null)) {
            problems.add("Аудиторные часы не найдены: в итоговой строке плана нет часов контактной работы");
            return null;
        }
        final List<Double> hours = total.hours;
        if (hours.get(0) != hours.get(1) + hours.get(2)
            || hours.get(2) != hours.get(CONTACT) + hours.get(CONTACT + 1)) {
            problems.add(
                "Аудиторные часы не определены: часы итоговой строки плана не сходятся (" + hours + ")"
            );
            return null;
        }
        return hours.get(CONTACT);
    }

    /** «240», «4,5». */
    public static String number(final double value) {
        return value == Math.rint(value)
            ? String.valueOf((long) value)
            : String.valueOf(value).replace('.', ',');
    }

    /** Credits of the whole program. */
    public Double program() {
        return this.program;
    }

    /** Credits of block 2, all practices. */
    public Double practices() {
        return this.practices;
    }

    /** Credits of block 3, the state final attestation. */
    public Double attestation() {
        return this.attestation;
    }

    /** Contact hours of the whole program. */
    public Double contact() {
        return this.contact;
    }

    /** What was not found or does not add up. */
    public List<String> problems() {
        return this.problems;
    }
}
