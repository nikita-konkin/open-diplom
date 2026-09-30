package org.opendiplom.plans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.opendiplom.plans.PlanItem.Kind;
import org.opendiplom.plans.PlanItem.Section;

/**
 * The sums a plan must keep, so that a plan read from a file or typed in by
 * hand is known to be whole.
 *
 * <p>In a row: credits = credits for exams + for classes, hours = credits ×
 * 36, hours = hours for exams + for classes, hours for classes = contact +
 * self-study. In the structure: each column of a row equals the sum of the
 * rows directly under it, and the program total equals blocks 1–3. A wrong
 * number in an element therefore shows once, at the row above it.
 *
 * <p>A row without credits is left out of the sums: the 328 hours of the
 * physical culture electives are not converted to credits, and «Планы» do not
 * add them to their part.
 */
public final class PlanCheck {
    /** Academic hours in a credit. */
    public static final double HOURS_PER_CREDIT = 36;
    private static final double SAME = 0.01;
    private static final List<String> CREDIT_NAMES =
        Arrays.asList("з.е.", "з.е. на экзамены", "з.е. на учебные занятия");
    private static final List<String> HOUR_NAMES = Arrays.asList(
        "часов", "часов на экзамены", "часов на учебные занятия", "часов контактной работы",
        "часов самостоятельной работы"
    );

    /** How bad a finding is. */
    public enum Level {
        /** A sum does not hold: the plan is wrong or misread. */
        ERROR,
        /** Something cannot be checked or looks unusual. */
        WARNING
    }

    /** What is wrong, and where. */
    public static final class Finding {
        private final Level level;
        private final int position;
        private final String message;

        Finding(final Level level, final int position, final String message) {
            this.level = level;
            this.position = position;
            this.message = message;
        }

        public Level level() {
            return this.level;
        }

        /** Position of the row, or -1 for the plan as a whole. */
        public int position() {
            return this.position;
        }

        public String message() {
            return this.message;
        }

        @Override
        public String toString() {
            return this.level + " " + this.message;
        }
    }

    private final List<Finding> findings;

    private PlanCheck(final List<Finding> findings) {
        this.findings = Collections.unmodifiableList(findings);
    }

    public static PlanCheck of(final PlanStructure plan) {
        final List<Finding> findings = new ArrayList<>();
        boolean detailed = false;
        for (final PlanItem item : plan.items()) {
            detailed |= row(item, findings);
        }
        if (!detailed) {
            findings.add(new Finding(
                Level.WARNING, -1,
                "В плане нет разбивки з.е. и часов по видам работы: суммы в строках не проверены"
            ));
        }
        for (final PlanItem item : plan.items()) {
            final List<PlanItem> children = plan.children(item);
            if (!children.isEmpty() && item.kind() != Kind.HEADING) {
                sums(item, children, "вложенных строк", findings);
            }
            if (item.leaf() && item.section() != Section.FACULTATIVES && item.row().credits() == null
                && item.row().hours().stream().allMatch(value -> value == null)) {
                findings.add(new Finding(
                    Level.WARNING, item.position(),
                    item.label() + ": нет ни з.е., ни часов — если это заголовок модуля, всё в порядке, "
                        + "иначе строка не попадёт в приложение"
                ));
            }
        }
        final PlanItem total = plan.total();
        if (total == null) {
            findings.add(new Finding(
                Level.WARNING, -1, "Нет строки «ОБЪЕМ ОБРАЗОВАТЕЛЬНОЙ ПРОГРАММЫ»: объём программы не проверен"
            ));
        } else if (plan.blocks().size() == 3) {
            sums(total, plan.blocks(), "блоков 1–3", findings);
        } else {
            findings.add(new Finding(
                Level.WARNING, total.position(),
                "Найдено блоков: " + plan.blocks().size() + " из 3, объём программы не сверен с блоками"
            ));
        }
        return new PlanCheck(findings);
    }

    public List<Finding> findings() {
        return this.findings;
    }

    /** Whether no sum is broken; warnings do not count. */
    public boolean passed() {
        return this.findings.stream().noneMatch(finding -> finding.level() == Level.ERROR);
    }

    public long errors() {
        return this.findings.stream().filter(finding -> finding.level() == Level.ERROR).count();
    }

    /**
     * The sums within a row.
     *
     * @return whether the row has any credits or hours broken down to check
     */
    private static boolean row(final PlanItem item, final List<Finding> findings) {
        final List<Double> credits = item.row().creditColumns();
        final List<Double> hours = item.row().hours();
        boolean detailed = false;
        if (credits.get(0) != null && (credits.get(1) != null || credits.get(2) != null)) {
            detailed = true;
            equal(item, credits.get(0), plus(credits.get(1), credits.get(2)),
                "з.е. " + PlanTotals.number(credits.get(0)) + ", а на экзамены и занятия вместе", findings);
        }
        if (credits.get(0) != null && hours.get(0) != null) {
            equal(item, hours.get(0), credits.get(0) * HOURS_PER_CREDIT,
                "часов " + PlanTotals.number(hours.get(0)) + ", а " + PlanTotals.number(credits.get(0))
                    + " з.е. × 36 =", findings);
        }
        if (hours.get(0) != null && (hours.get(1) != null || hours.get(2) != null)) {
            detailed = true;
            equal(item, hours.get(0), plus(hours.get(1), hours.get(2)),
                "часов " + PlanTotals.number(hours.get(0)) + ", а на экзамены и занятия вместе", findings);
        }
        if (hours.get(2) != null && (hours.get(PlanRow.CONTACT) != null || hours.get(PlanRow.CONTACT + 1) != null)) {
            detailed = true;
            equal(item, hours.get(2), plus(hours.get(PlanRow.CONTACT), hours.get(PlanRow.CONTACT + 1)),
                "часов на занятия " + PlanTotals.number(hours.get(2)) + ", а контактной и самостоятельной вместе",
                findings);
        }
        return detailed;
    }

    /** Each column of a row against the sum of the same column of its parts. */
    private static void sums(
        final PlanItem item, final List<PlanItem> parts, final String what, final List<Finding> findings
    ) {
        column(item, parts, true, what, findings);
        column(item, parts, false, what, findings);
    }

    private static void column(
        final PlanItem item, final List<PlanItem> parts, final boolean credits, final String what,
        final List<Finding> findings
    ) {
        final List<String> names = credits ? CREDIT_NAMES : HOUR_NAMES;
        for (int column = 0; column < names.size(); ++column) {
            final Double value = values(item, credits).get(column);
            Double sum = null;
            for (final PlanItem part : parts) {
                if (part.row().credits() != null) {
                    sum = plus(sum, values(part, credits).get(column));
                }
            }
            if (value != null && sum != null) {
                equal(item, value, sum,
                    names.get(column) + " " + PlanTotals.number(value) + ", а сумма " + what, findings);
            } else if (value == null && sum != null && sum != 0 && column == 0) {
                findings.add(new Finding(
                    Level.WARNING, item.position(),
                    item.label() + ": " + names.get(column) + " не указано, а сумма " + what + " — "
                        + PlanTotals.number(sum)
                ));
            }
        }
    }

    private static List<Double> values(final PlanItem item, final boolean credits) {
        return credits ? item.row().creditColumns() : item.row().hours();
    }

    private static void equal(
        final PlanItem item, final double value, final Double expected, final String message,
        final List<Finding> findings
    ) {
        final double other = expected == null ? 0 : expected;
        if (Math.abs(value - other) > SAME) {
            findings.add(new Finding(
                Level.ERROR, item.position(), item.label() + ": " + message + " " + PlanTotals.number(other)
            ));
        }
    }

    /** A sum where an empty cell is zero and two empty cells are empty. */
    private static Double plus(final Double left, final Double right) {
        if (left == null) {
            return right;
        }
        return right == null ? left : left + right;
    }
}
