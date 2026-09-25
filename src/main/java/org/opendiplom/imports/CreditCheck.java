package org.opendiplom.imports;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Where the credits of one subject come from and what the operator must check.
 *
 * <p>Credits come from the curriculum. Without a readable curriculum, or for a
 * subject missing from it, they stay as counted from the statement hours and
 * the subject is marked for the operator (B-31).
 */
public final class CreditCheck {
    public static final String FROM_PLAN = "учебный план";
    public static final String FROM_HOURS = "часы ведомости";

    private final String label;
    private final String settled;
    private final int counted;
    private final Double planned;
    private final String source;
    private final List<String> notes;

    private CreditCheck(
        final String label, final String settled, final int counted,
        final Double planned, final String source, final List<String> notes
    ) {
        this.label = label;
        this.settled = settled;
        this.counted = counted;
        this.planned = planned;
        this.source = source;
        this.notes = notes;
    }

    /**
     * Checks for every subject of the statement that has credits; course work
     * has none in the supplement and is left out.
     *
     * @param curriculum the plan, {@code null} when it was not loaded or not read
     * @param planProblem why there is no plan, for the notes
     */
    public static List<CreditCheck> settle(
        final StatementImport statement, final Curriculum curriculum, final String planProblem
    ) {
        final List<CreditCheck> checks = new ArrayList<>();
        final List<String> settled = new ArrayList<>();
        for (final String label : statement.labels()) {
            final Matcher match = Credits.LABEL.matcher(label);
            if (!match.matches() || Kind.COURSE_WORK.title().equals(match.group(2))) {
                settled.add(label);
                continue;
            }
            final String name = match.group(1);
            final int counted = Integer.parseInt(match.group(3));
            final List<String> names = new ArrayList<>(Arrays.asList(null, name));
            if (name.contains(". ")) {
                names.add(name.split("\\. ", 2)[1]);
            }
            final Double planned = curriculum == null ? null : curriculum.find(names);
            final List<String> notes = new ArrayList<>();
            final int credits;
            final String source;
            final String own = label.replace("_факультатив_", "_дисциплина_");
            if (planned != null && planned == Math.rint(planned)) {
                credits = planned.intValue();
                source = FROM_PLAN;
                if (credits != counted) {
                    notes.add("по часам ведомости " + counted + " з.е. — тот ли учебный план?");
                }
            } else {
                credits = counted;
                source = FROM_HOURS;
                if (curriculum == null) {
                    notes.add(planProblem);
                } else if (planned == null) {
                    notes.add("нет в учебном плане");
                } else {
                    notes.add("в учебном плане дробные з.е. (" + general(planned) + ")");
                }
                final Double spent = statement.hours(own);
                if (spent != null && spent != 0 && spent % Credits.HOURS_PER_CREDIT != 0) {
                    notes.add("часы ведомости не кратны 36 (" + general(spent) + " ч)");
                }
            }
            final int missing = statement.ungraded(own);
            if (missing > 0) {
                notes.add("у " + missing + " студ. в ведомости есть семестр без оценки");
            }
            final String result = name + '_' + match.group(2) + '_' + credits;
            settled.add(result);
            checks.add(new CreditCheck(label, result, counted, planned, source, notes));
        }
        // with semesters summed, a repeated subject would count its credits twice
        for (final CreditCheck check : checks) {
            final int repeats = Collections.frequency(settled, check.settled);
            if (repeats > 1) {
                check.notes.add("строка повторяется в сводной " + repeats + " раза — оставьте одну");
            }
        }
        return checks;
    }

    /** Label as read from the statement. */
    public String label() {
        return this.label;
    }

    /** Label with the settled credits. */
    public String settled() {
        return this.settled;
    }

    /** Credits counted from the statement hours. */
    public int counted() {
        return this.counted;
    }

    /** Credits in the curriculum, {@code null} when the subject was not found. */
    public Double planned() {
        return this.planned;
    }

    /** {@link #FROM_PLAN} or {@link #FROM_HOURS}. */
    public String source() {
        return this.source;
    }

    /** What to check, joined; empty when nothing. */
    public String notes() {
        return String.join("; ", this.notes);
    }

    /** A number as Python's {@code format(x, "g")} writes it. */
    static String general(final double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e16) {
            return Long.toString((long) value);
        }
        return new BigDecimal(value).round(new MathContext(6)).stripTrailingZeros().toPlainString();
    }
}
