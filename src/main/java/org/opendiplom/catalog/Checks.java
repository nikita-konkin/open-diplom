package org.opendiplom.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.opendiplom.export.Names;
import org.opendiplom.export.StudentInfo;
import org.opendiplom.plans.PlanItem;
import org.opendiplom.plans.PlanRow;
import org.opendiplom.plans.PlanStructure;
import org.opendiplom.plans.PlanTotals;

/**
 * What stands between a graduate and the supplement.
 *
 * <p>An error keeps the graduate out of the XML. Code 7 «не выполнял» is a
 * placeholder until the real grade is known (D-01): the graduate is not
 * ready, but the XML may carry it. «1111» and names mixing alphabets are
 * warnings.
 */
public final class Checks {
    private static final Pattern YEAR = Pattern.compile("\\d{4}");
    private static final int PLACEHOLDER = 7;
    private static final double SAME = 1e-6;

    /** How much a finding matters. */
    public enum Level {
        ERROR("ошибка"),
        UNFINISHED("не завершено"),
        WARNING("предупреждение");

        private final String title;

        Level(final String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    /** One thing to fix or to know. */
    public static final class Finding {
        public final Level level;
        public final String message;

        Finding(final Level level, final String message) {
            this.level = level;
            this.message = message;
        }

        @Override
        public String toString() {
            return this.level + " " + this.message;
        }
    }

    private Checks() {
    }

    /** Findings of a graduate against the plan of the graduation, errors first. */
    public static List<Finding> of(final GraduateRecord graduate, final PlanStructure plan, final PlanTotals totals) {
        final List<Finding> findings = new ArrayList<>();
        required(graduate, findings);
        attestation(graduate, plan, findings);
        grades(graduate, findings);
        coverage(graduate, plan, findings);
        credits(graduate, totals, findings);
        findings.sort((left, right) -> left.level.compareTo(right.level));
        return Collections.unmodifiableList(findings);
    }

    /** Ready for the supplement: no error and no placeholder. */
    public static boolean ready(final List<Finding> findings) {
        return findings.stream().allMatch(finding -> finding.level == Level.WARNING);
    }

    /** May go to the XML: no error. */
    public static boolean exportable(final List<Finding> findings) {
        return findings.stream().noneMatch(finding -> finding.level == Level.ERROR);
    }

    /** Findings of a level. */
    public static long count(final List<Finding> findings, final Level level) {
        return findings.stream().filter(finding -> finding.level == level).count();
    }

    /** Whether the attestation of a plan has a state exam besides the thesis. */
    public static boolean stateExam(final PlanStructure plan) {
        for (final PlanItem item : plan.leaves()) {
            final String key = PlanRow.key(item.row().name());
            if (item.section() == PlanItem.Section.ATTESTATION && key.contains("государственн") && key.contains("экзамен")) {
                return true;
            }
        }
        return false;
    }

    private static void required(final GraduateRecord graduate, final List<Finding> findings) {
        empty(graduate.birthDate, "дата рождения не заполнена или не распознана", findings);
        empty(graduate.previousDocument, "не заполнено наименование документа о предыдущем образовании", findings);
        empty(graduate.gekDate, "дата решения ГЭК не заполнена или не распознана", findings);
        empty(graduate.gekProtocol, "не заполнен номер протокола ГЭК", findings);
        if (StudentInfo.YEAR_PLACEHOLDER.equals(graduate.previousYear)) {
            findings.add(new Finding(Level.WARNING,
                "год документа о предыдущем образовании — заглушка " + StudentInfo.YEAR_PLACEHOLDER + " «год неизвестен»"));
        } else if (!YEAR.matcher(graduate.previousYear).matches()) {
            findings.add(new Finding(Level.ERROR,
                "год документа о предыдущем образовании «" + graduate.previousYear + "» — нужен год из четырёх цифр"));
        }
        final List<String> mixed = Names.mixedScript(graduate.fullName());
        if (!mixed.isEmpty()) {
            findings.add(new Finding(Level.WARNING,
                "в ФИО смешаны латинские и русские буквы: " + String.join(", ", mixed)));
        }
    }

    private static void attestation(final GraduateRecord graduate, final PlanStructure plan, final List<Finding> findings) {
        empty(graduate.thesisTopic, "не заполнена тема ВКР", findings);
        grade(graduate.thesisGrade, "ВКР", "нет оценки ВКР", findings);
        if (stateExam(plan)) {
            grade(graduate.stateExamGrade, "государственный экзамен",
                "нет оценки за государственный экзамен: заполните колонку «" + StudentInfo.STATE_EXAM
                    + "» файла сведений", findings);
        } else if (graduate.stateExamGrade != null) {
            findings.add(new Finding(Level.WARNING,
                "в учебном плане нет государственного экзамена: его оценка не попадёт в XML"));
        }
    }

    private static void grades(final GraduateRecord graduate, final List<Finding> findings) {
        for (final ResultRecord result : graduate.results) {
            final String what = "«" + result.printed + "»";
            if (result.grade == null && result.gradeText.isEmpty() && ResultRecord.FACULTATIVE.equals(result.kind)) {
                findings.add(new Finding(Level.WARNING,
                    what + ": в ведомости без оценки, факультатив не попадёт в приложение"));
            } else if (result.grade == null && !result.gradeText.isEmpty()) {
                findings.add(new Finding(Level.ERROR,
                    what + ": недопустимая оценка «" + result.gradeText + "», допустимы коды 2–7"));
            } else {
                grade(result.grade, what, what + ": нет оценки в ведомости", findings);
            }
        }
    }

    /**
     * Every element a graduate studies has a result: a discipline or a
     * practice with credits, and a course work where the plan has one. A
     * result of a group covers its rows; of an elective group, one row is enough.
     */
    private static void coverage(final GraduateRecord graduate, final PlanStructure plan, final List<Finding> findings) {
        for (final PlanItem item : plan.leaves()) {
            final PlanItem.Section section = item.section();
            if (section != PlanItem.Section.DISCIPLINES && section != PlanItem.Section.PRACTICES) {
                continue;
            }
            if (item.row().credits() != null && !covered(graduate, plan, item, false)
                && !chosenElsewhere(graduate, plan, item)) {
                findings.add(new Finding(Level.ERROR, item.label() + ": нет оценки"));
            }
            final List<String> controls = item.row().controls();
            final boolean work = !controls.get(PlanRow.COURSE_WORKS).isEmpty();
            if ((work || !controls.get(PlanRow.COURSE_PROJECTS).isEmpty()) && !covered(graduate, plan, item, true)) {
                findings.add(new Finding(Level.ERROR,
                    item.label() + ": нет оценки за курсов" + (work ? "ую работу" : "ой проект")));
            }
        }
    }

    private static boolean covered(
        final GraduateRecord graduate, final PlanStructure plan, final PlanItem item, final boolean courseWork
    ) {
        for (final ResultRecord result : graduate.results) {
            if (ResultRecord.COURSE_WORK.equals(result.kind) != courseWork) {
                continue;
            }
            for (int place = item.position(); place >= 0; place = plan.items().get(place).parent()) {
                if (result.element == place) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A row of an elective group — rows of equal credits under an element — when another row has the result. */
    private static boolean chosenElsewhere(final GraduateRecord graduate, final PlanStructure plan, final PlanItem item) {
        if (item.parent() < 0) {
            return false;
        }
        final PlanItem group = plan.items().get(item.parent());
        final List<PlanItem> rows = plan.children(group);
        if (group.kind() != PlanItem.Kind.ELEMENT || rows.size() < 2) {
            return false;
        }
        for (final PlanItem row : rows) {
            if (!row.leaf() || row.row().credits() == null || !row.row().credits().equals(group.row().credits())) {
                return false;
            }
        }
        for (final PlanItem row : rows) {
            if (covered(graduate, plan, row, false)) {
                return true;
            }
        }
        return false;
    }

    /** Credits of the disciplines and practices with the attestation make the program. */
    private static void credits(final GraduateRecord graduate, final PlanTotals totals, final List<Finding> findings) {
        if (totals.program() == null || totals.attestation() == null) {
            findings.add(new Finding(Level.WARNING, "в плане не найден объём программы или ГИА: сумма з.е. не проверена"));
            return;
        }
        double sum = 0;
        for (final ResultRecord result : graduate.results) {
            if ((ResultRecord.DISCIPLINE.equals(result.kind) || ResultRecord.PRACTICE.equals(result.kind))
                && result.credits != null) {
                sum += result.credits;
            }
        }
        if (Math.abs(sum + totals.attestation() - totals.program()) > SAME) {
            findings.add(new Finding(Level.ERROR,
                "сумма з.е.: дисциплины и практики " + PlanTotals.number(sum) + " + ГИА "
                    + PlanTotals.number(totals.attestation()) + " = " + PlanTotals.number(sum + totals.attestation())
                    + ", а объём программы " + PlanTotals.number(totals.program())));
        }
    }

    private static void grade(final Integer grade, final String what, final String missing, final List<Finding> findings) {
        if (grade == null) {
            findings.add(new Finding(Level.ERROR, missing));
        } else if (grade == PLACEHOLDER) {
            findings.add(new Finding(Level.UNFINISHED, what + ": код 7 «не выполнял» — заглушка до настоящей оценки"));
        }
    }

    private static void empty(final String value, final String message, final List<Finding> findings) {
        if (value == null || value.isBlank()) {
            findings.add(new Finding(Level.ERROR, message));
        }
    }
}
