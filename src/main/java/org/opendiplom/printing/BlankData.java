package org.opendiplom.printing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opendiplom.catalog.DocumentRecord;
import org.opendiplom.catalog.GraduateRecord;
import org.opendiplom.catalog.Organization;
import org.opendiplom.catalog.ResultRecord;
import org.opendiplom.export.Program;
import org.opendiplom.export.ValidationProblems;
import org.opendiplom.plans.PlanTotals;

/**
 * What the blanks of a graduate print, as the data sets of CyberDiploma that
 * the templates read: {@code Студент}, {@code Документ},
 * {@code Образовательное учреждение} have one row; {@code Модули и разделы},
 * {@code Курсовые работы} and {@code Дополнительные сведения} have many.
 *
 * <p>The rows of the table follow the supplements CyberDiploma printed in
 * 2026: disciplines, practices, the attestation, the volume of the program
 * and the contact hours, course works, facultatives. A section prints «x» in
 * the column it has no value for, the attestation «х», as they did.
 */
public final class BlankData {
    public static final String STUDENT = "Студент";
    public static final String DOCUMENT = "Документ";
    public static final String ORGANIZATION = "Образовательное учреждение";
    public static final String MODULES = "Модули и разделы";
    public static final String COURSE_WORKS = "Курсовые работы";
    public static final String EXTRA = "Дополнительные сведения";
    private static final String NAME = "Наименование";
    private static final String CREDITS = "Трудоёмкость";
    private static final String GRADE = "Оценка";
    /** Latin, as CyberDiploma printed it in the rows of sections. */
    private static final String NONE = "x";
    /** Cyrillic, as CyberDiploma printed it for the attestation. */
    private static final String NONE_ATTESTATION = "х";
    private static final String INCLUDING = "в том числе:";

    private final Map<String, Object> fields = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> rows = new LinkedHashMap<>();

    private BlankData() {
    }

    /**
     * The data of a document.
     *
     * @param honors «с отличием» as decided: {@link DocumentRecord#honors}
     * @param stateExam whether the attestation of the plan has a state exam
     * @param courseWorksApart whether the template prints course works in a band of their own;
     *     otherwise they are rows of the table, after the contact hours
     */
    public static BlankData of(
        final Program program, final GraduateRecord graduate, final DocumentRecord document, final boolean honors,
        final Organization organization, final boolean stateExam, final boolean courseWorksApart
    ) {
        final BlankData data = new BlankData();
        data.student(program, graduate);
        data.put(DOCUMENT, "Регистрационный номер", document.regNumber);
        data.put(DOCUMENT, "Дата выдачи", document.issueDate);
        data.put(DOCUMENT, "Диплом: С отличием", honors ? 1L : 0L);
        data.put(DOCUMENT, "Диплом: дубликат", document.diplomaDuplicate ? 1L : 0L);
        data.put(DOCUMENT, "Приложение: Дубликат", document.supplementDuplicate ? 1L : 0L);
        data.put(ORGANIZATION, NAME, organization.fullName);
        data.put(ORGANIZATION, "Местонахождение", organization.locality);
        data.put(ORGANIZATION, "Руководитель: Фамилия", organization.headLastName);
        data.put(ORGANIZATION, "Руководитель: Имя", organization.headFirstName);
        data.put(ORGANIZATION, "Руководитель: Отчество", organization.headMiddleName);
        data.table(program, graduate, stateExam, courseWorksApart);
        data.row(EXTRA, NAME, "Направленность (профиль) образовательной программы: \"" + program.profile() + "\"");
        data.row(EXTRA, NAME, "Форма обучения: " + program.studyForm());
        return data;
    }

    /**
     * Made-up data of a level for trying a template and the printer: «03»
     * бакалавриат, «04» магистратура, «05» специалитет.
     *
     * @param organization the organization, when filled in, else a made-up one
     */
    public static BlankData sample(final String level, final Organization organization, final boolean courseWorksApart) {
        final String code;
        final String qualification;
        if ("04".equals(level)) {
            code = "09.04.02 Информационные системы и технологии";
            qualification = "магистр";
        } else if ("05".equals(level)) {
            code = "10.05.03 Информационная безопасность автоматизированных систем";
            qualification = "специалист по защите информации";
        } else {
            code = "09.03.02 Информационные системы и технологии";
            qualification = "бакалавр";
        }
        final Program program;
        try {
            program = new Program.Builder().direction(code).profile("Информационные системы в управлении")
                .qualification(qualification).studyForm("очная").studyTerm("04".equals(level) ? "2 года" : "4 года")
                .programCredits("04".equals(level) ? "120" : "240").contactHours("3180 ак.час").practiceCredits("21")
                .finalCredits("9").gekChairman("Сидоров Сергей Сергеевич").build();
        } catch (final ValidationProblems error) {
            throw new IllegalStateException("Образец программы не прошёл проверку", error);
        }
        final List<ResultRecord> results = new ArrayList<>();
        results.add(new ResultRecord(0, ResultRecord.DISCIPLINE, "История России", 5, "5", 4.0));
        results.add(new ResultRecord(1, ResultRecord.DISCIPLINE, "Иностранный язык", 6, "6", 6.0));
        results.add(new ResultRecord(2, ResultRecord.DISCIPLINE,
            "Проектирование информационных систем и баз данных предприятий и организаций", 4, "4", 5.0));
        results.add(new ResultRecord(3, ResultRecord.COURSE_WORK,
            "Проектирование информационных систем и баз данных предприятий и организаций (курсовая работа)", 5, "5",
            null));
        results.add(new ResultRecord(4, ResultRecord.PRACTICE, "Учебная практика (ознакомительная практика)", 6,
            "6", 3.0));
        results.add(new ResultRecord(5, ResultRecord.PRACTICE, "Производственная практика (преддипломная практика)",
            5, "5", 9.0));
        results.add(new ResultRecord(6, ResultRecord.FACULTATIVE, "Основы делового общения", 6, "6", 2.0));
        final GraduateRecord graduate = new GraduateRecord(
            "sample", 0, "Образцова", "Анна", "Сергеевна", "2004-03-15", "Аттестат о среднем общем образовании",
            "2021", LocalDate.now().toString(), "17", "Разработка информационной системы учёта выпускников вуза", 5, 5,
            "Образцова А. С.", "0000000000", "", results
        );
        final Organization issuer = organization.missing().isEmpty() ? organization : new Organization(
            "федеральное государственное бюджетное\nобразовательное учреждение высшего образования\n"
                + "«Образцовый государственный университет»", "г. Образцово", "Петров", "Пётр", "Петрович"
        );
        final DocumentRecord document = new DocumentRecord(
            null, "sample", null, false, false, "10001", LocalDate.now().toString(), null
        );
        return of(program, graduate, document, false, issuer, true, courseWorksApart);
    }

    /** Data given as it is, for tests and tools: fields by {@code Набор."Поле"} and rows by data set. */
    public static BlankData of(final Map<String, Object> fields, final Map<String, List<Map<String, Object>>> rows) {
        final BlankData data = new BlankData();
        data.fields.putAll(fields);
        data.rows.putAll(rows);
        return data;
    }

    /** The fields of the data sets with one row, by {@code Набор."Поле"}. */
    public Map<String, Object> fields() {
        return Collections.unmodifiableMap(this.fields);
    }

    /**
     * The rows of a data set, each with its fields by {@code Набор."Поле"};
     * a data set of one row gives that row, an unknown one none.
     */
    public List<Map<String, Object>> rows(final String dataset) {
        if (STUDENT.equals(dataset) || DOCUMENT.equals(dataset) || ORGANIZATION.equals(dataset)) {
            return Collections.singletonList(Collections.emptyMap());
        }
        return Collections.unmodifiableList(this.rows.getOrDefault(dataset, Collections.emptyList()));
    }

    /** Whether the data has such a data set. */
    public boolean has(final String dataset) {
        return STUDENT.equals(dataset) || DOCUMENT.equals(dataset) || ORGANIZATION.equals(dataset)
            || MODULES.equals(dataset) || COURSE_WORKS.equals(dataset) || EXTRA.equals(dataset);
    }

    /** «отлично», «хорошо», «удовлетворительно», «зачтено»; empty for a code that is not printed. */
    public static String grade(final Integer code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case 2:
                return "неудовлетворительно";
            case 3:
                return "удовлетворительно";
            case 4:
                return "хорошо";
            case 5:
                return "отлично";
            case 6:
                return "зачтено";
            default:
                return "";
        }
    }

    /**
     * The chairman as surname, name and patronymic; initials stay letters:
     * «Иванов Иван Иванович», «Иванов И.И.», «И. И. Иванов».
     */
    static String[] person(final String text) {
        final List<String> words = new ArrayList<>();
        final List<String> initials = new ArrayList<>();
        for (final String part : text.replace(".", ". ").strip().split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (part.endsWith(".") && part.length() <= 3) {
                initials.add(part.substring(0, part.length() - 1));
            } else {
                words.add(part);
            }
        }
        if (initials.isEmpty()) {
            return new String[] {
                words.isEmpty() ? "" : words.get(0), words.size() > 1 ? words.get(1) : "",
                words.size() > 2 ? String.join(" ", words.subList(2, words.size())) : "",
            };
        }
        return new String[] {
            String.join(" ", words), initials.get(0), initials.size() > 1 ? initials.get(1) : "",
        };
    }

    private void student(final Program program, final GraduateRecord graduate) {
        this.put(STUDENT, "Фамилия", graduate.lastName);
        this.put(STUDENT, "Имя", graduate.firstName);
        this.put(STUDENT, "Отчество", graduate.middleName);
        this.put(STUDENT, "Дата рождения", graduate.birthDate);
        this.put(STUDENT, "Предыдущий документ: Наименование", graduate.previousDocument);
        this.put(STUDENT, "Предыдущий документ: Год выдачи", graduate.previousYear);
        this.put(STUDENT, "Специальность: Код", program.directionCode());
        this.put(STUDENT, "Специальность: Наименование", program.directionName());
        this.put(STUDENT, "Квалификация", program.qualification());
        this.put(STUDENT, "Срок обучения", program.studyTerm());
        final String[] chairman = person(program.gekChairman());
        this.put(STUDENT, "Председатель: Фамилия", chairman[0]);
        this.put(STUDENT, "Председатель: Имя", chairman[1]);
        this.put(STUDENT, "Председатель: Отчество", chairman[2]);
        this.put(STUDENT, "Дата решения госкомиссии", graduate.gekDate);
        this.put(STUDENT, "Номер протокола госкомиссии", graduate.gekProtocol);
    }

    private void table(
        final Program program, final GraduateRecord graduate, final boolean stateExam, final boolean courseWorksApart
    ) {
        this.results(graduate, ResultRecord.DISCIPLINE, true);
        final List<ResultRecord> practices = graded(graduate, ResultRecord.PRACTICE);
        if (!practices.isEmpty() || !program.practiceCredits().isEmpty()) {
            this.module("Практики", credits(program.practiceCredits()), NONE);
            this.module(INCLUDING, "", "");
            this.results(graduate, ResultRecord.PRACTICE, true);
        }
        this.module("Государственная итоговая аттестация", credits(program.finalCredits()), NONE);
        this.module(INCLUDING, "", "");
        if (stateExam && graduate.stateExamGrade != null) {
            this.module("Государственный экзамен", NONE_ATTESTATION, grade(graduate.stateExamGrade));
        }
        this.module(
            "Выпускная квалификационная работа \"" + graduate.thesisTopic + "\"", NONE_ATTESTATION,
            grade(graduate.thesisGrade)
        );
        this.module("Объем образовательной программы", credits(program.programCredits()), NONE);
        this.module(
            "в том числе объем контактной работы обучающихся во взаимодействии с преподавателем "
                + "в академических часах:", program.contactHours(), NONE
        );
        if (courseWorksApart) {
            for (final ResultRecord work : graded(graduate, ResultRecord.COURSE_WORK)) {
                final Map<String, Object> row = new LinkedHashMap<>();
                row.put(key(COURSE_WORKS, NAME), work.printed);
                row.put(key(COURSE_WORKS, GRADE), grade(work.grade));
                this.rows.computeIfAbsent(COURSE_WORKS, set -> new ArrayList<>()).add(row);
            }
        } else {
            this.results(graduate, ResultRecord.COURSE_WORK, false);
        }
        if (!graded(graduate, ResultRecord.FACULTATIVE).isEmpty()) {
            this.module("Факультативные дисциплины", "", "");
            this.module(INCLUDING, "", "");
            this.results(graduate, ResultRecord.FACULTATIVE, true);
        }
    }

    private void results(final GraduateRecord graduate, final String kind, final boolean credits) {
        for (final ResultRecord result : graded(graduate, kind)) {
            this.module(
                result.printed, credits && result.credits != null ? credits(PlanTotals.number(result.credits)) : "",
                grade(result.grade)
            );
        }
    }

    /** A facultative without a grade is left out, as in the XML. */
    private static List<ResultRecord> graded(final GraduateRecord graduate, final String kind) {
        final List<ResultRecord> found = new ArrayList<>();
        for (final ResultRecord result : graduate.results) {
            if (kind.equals(result.kind) && result.grade != null) {
                found.add(result);
            }
        }
        return found;
    }

    private void module(final String name, final String credits, final String grade) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put(key(MODULES, NAME), name);
        row.put(key(MODULES, CREDITS), credits);
        row.put(key(MODULES, GRADE), grade);
        this.rows.computeIfAbsent(MODULES, set -> new ArrayList<>()).add(row);
    }

    private void row(final String dataset, final String field, final String value) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put(key(dataset, field), value);
        this.rows.computeIfAbsent(dataset, set -> new ArrayList<>()).add(row);
    }

    private void put(final String dataset, final String field, final Object value) {
        this.fields.put(key(dataset, field), value == null ? "" : value);
    }

    private static String credits(final String number) {
        return number.isEmpty() ? "" : number + " з.е.";
    }

    static String key(final String dataset, final String field) {
        return dataset + ".\"" + field + '"';
    }
}
