package org.opendiplom.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.opendiplom.catalog.Numbers;
import org.opendiplom.graduation.Edits;

/**
 * Blanks of strict accounting (ADR-0013; Order No 670, items 47–50): the
 * batches the typographer delivered, the blank each document is printed on,
 * the blanks spoiled and why, and how each document was handed (item 29).
 *
 * <p>A document printed on a blank and not handed yet does not change: what
 * would change the printed text is refused until the blank is marked
 * spoiled. A handed one never changes; an error in it takes a duplicate.
 */
public final class Stock {
    public static final String FREE = "free";
    public static final String USED = "used";
    public static final String SPOILED = "spoiled";
    public static final String PERSON = "person";
    public static final String ATTORNEY = "attorney";
    public static final String POST = "post";
    /** Ways of handing a document, with their titles. */
    public static final Map<String, String> WAYS;
    static final String BLANKS = "blanks";
    static final String ISSUE = "issue";
    private static final Map<String, String> STATUSES = Map.of(FREE, "свободен", USED, "использован", SPOILED, "испорчен");
    /** Columns of a graduate printed on the diploma as well as on the supplement. */
    private static final Set<String> ON_DIPLOMA = Set.of("last_name", "first_name", "middle_name", "gek_date", "gek_protocol");
    private static final Pattern SERIES = Pattern.compile("\\d{6}");
    private static final Pattern NUMBER = Pattern.compile("\\d{7}");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final String SUBJECT = "stock";

    static {
        final Map<String, String> ways = new LinkedHashMap<>();
        ways.put(PERSON, "лично");
        ways.put(ATTORNEY, "по доверенности");
        ways.put(POST, "почтой");
        WAYS = Collections.unmodifiableMap(ways);
    }

    private final Database database;

    public Stock(final Database database) {
        this.database = database;
    }

    /** A form of blank (Order No 645): a diploma of a level, «с отличием» or not, or a supplement of a level. */
    public static final class Form {
        private static final Map<String, String> OF = Map.of("03", "бакалавра", "04", "магистра", "05", "специалиста");

        public final String kind;
        public final String level;
        public final boolean honors;

        public Form(final String kind, final String level, final boolean honors) {
            this.kind = kind;
            this.level = level;
            this.honors = Blanks.DIPLOMA.equals(kind) && honors;
        }

        /** Every form, by level: the diploma, the diploma «с отличием», the supplement. */
        public static List<Form> all() {
            final List<Form> all = new ArrayList<>();
            for (final String level : Blanks.LEVELS.keySet()) {
                all.add(new Form(Blanks.DIPLOMA, level, false));
                all.add(new Form(Blanks.DIPLOMA, level, true));
                all.add(new Form(Blanks.SUPPLEMENT, level, false));
            }
            return all;
        }

        /** The form of a code, {@code null} when there is none such. */
        public static Form of(final String code) {
            for (final Form form : all()) {
                if (form.code().equals(code)) {
                    return form;
                }
            }
            return null;
        }

        /** «diploma-03», «diploma-03-honors», «supplement-03». */
        public String code() {
            return this.kind + "-" + this.level + (this.honors ? "-honors" : "");
        }

        /** «Диплом бакалавра с отличием», «Приложение к диплому магистра». */
        public String title() {
            final String level = OF.getOrDefault(this.level, this.level);
            return Blanks.DIPLOMA.equals(this.kind)
                ? "Диплом " + level + (this.honors ? " с отличием" : "")
                : "Приложение к диплому " + level;
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof Form && ((Form) other).code().equals(this.code());
        }

        @Override
        public int hashCode() {
            return this.code().hashCode();
        }
    }

    /** One blank with what became of it. */
    public static final class Blank {
        public final Form form;
        public final String series;
        public final String number;
        public final String status;
        /** The document printed on it, or spoiled with it; {@code null} when none. */
        public final String documentId;
        /** The registration number of that document, empty when none. */
        public final String regNumber;
        public final String reason;
        public final String changedAt;
        /** When its batch came, ГГГГ-ММ-ДД. */
        public final String received;

        Blank(
            final Form form, final String series, final String number, final String status, final String documentId,
            final String regNumber, final String reason, final String changedAt, final String received
        ) {
            this.form = form;
            this.series = series;
            this.number = number;
            this.status = status;
            this.documentId = documentId;
            this.regNumber = regNumber == null ? "" : regNumber;
            this.reason = reason;
            this.changedAt = changedAt;
            this.received = received;
        }

        /** «123456 0000101». */
        public String label() {
            return this.series + " " + this.number;
        }

        /** «свободен», «использован», «испорчен». */
        public String state() {
            return STATUSES.getOrDefault(this.status, this.status);
        }
    }

    /** Blanks that came together. */
    public static final class Batch {
        public final String id;
        public final Form form;
        public final String series;
        public final String numbers;
        public final int quantity;
        public final String received;
        public final String note;

        Batch(final String id, final Form form, final String series, final String numbers, final int quantity,
            final String received, final String note) {
            this.id = id;
            this.form = form;
            this.series = series;
            this.numbers = numbers;
            this.quantity = quantity;
            this.received = received;
            this.note = note;
        }
    }

    /** How many blanks of a form are free, used and spoiled. */
    public static final class Count {
        public final Form form;
        public final int free;
        public final int used;
        public final int spoiled;

        Count(final Form form, final int free, final int used, final int spoiled) {
            this.form = form;
            this.free = free;
            this.used = used;
            this.spoiled = spoiled;
        }
    }

    /** Free blanks of a form in one series: how many and the lowest number. */
    public static final class Free {
        public final String series;
        public final String first;
        public final int count;

        Free(final String series, final String first, final int count) {
            this.series = series;
            this.first = first;
            this.count = count;
        }
    }

    /** How a document was handed. */
    public static final class Issue {
        public final String documentId;
        /** ГГГГ-ММ-ДД. */
        public final String handed;
        public final String way;
        public final String details;

        Issue(final String documentId, final String handed, final String way, final String details) {
            this.documentId = documentId;
            this.handed = handed;
            this.way = way;
            this.details = details;
        }

        /** «лично», «по доверенности № 12 от 01.07.2026». */
        public String text() {
            final String way = WAYS.getOrDefault(this.way, this.way);
            return this.details.isEmpty() ? way : way + " " + this.details;
        }
    }

    /**
     * Blanks loaded in the printer in the order of their numbers: from the
     * first one, the free blanks of the form and series go to the documents in
     * the order given, as they were printed.
     */
    public static final class Run {
        public final Form form;
        public final String series;
        public final String first;
        public final List<String> documents;

        public Run(final Form form, final String series, final String first, final List<String> documents) {
            this.form = form;
            this.series = series;
            this.first = first;
            this.documents = documents;
        }
    }

    /**
     * Takes a batch of blanks in.
     *
     * @param numbers seven digits each, in a row or with gaps: «0000101–0000150, 0000160»
     * @return how many blanks came
     * @throws IllegalArgumentException on a series or a number not as Order No 645 has them, or a blank there already
     */
    public int receive(
        final Form form, final String series, final String numbers, final String received, final String note
    ) throws SQLException {
        if (!SERIES.matcher(series).matches()) {
            throw new IllegalArgumentException("Серия «" + series + "»: нужны шесть цифр");
        }
        if (!DATE.matcher(received).matches()) {
            throw new IllegalArgumentException("Дата получения «" + received + "»: нужна дата ГГГГ-ММ-ДД");
        }
        final List<String> all = Numbers.parse(numbers);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("Не указаны номера бланков");
        }
        for (final String number : all) {
            if (!NUMBER.matcher(number).matches()) {
                throw new IllegalArgumentException("Номер бланка «" + number + "»: нужны семь цифр, с нулями впереди");
            }
        }
        final String now = Instant.now().toString();
        final String id = UUID.randomUUID().toString();
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final List<String> known = new ArrayList<>();
            try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM blank WHERE form = ? AND series = ? AND number = ?"
            )) {
                for (final String number : all) {
                    query.setString(1, form.code());
                    query.setString(2, series);
                    query.setString(3, number);
                    try (ResultSet row = query.executeQuery()) {
                        if (row.next()) {
                            known.add(number);
                        }
                    }
                }
            }
            if (!known.isEmpty()) {
                throw new IllegalArgumentException("Бланки «" + form.title() + "» серии " + series + " уже учтены: "
                    + span(known));
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO blank_batch (id, form, series, numbers, quantity, received, note, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            )) {
                insert.setString(1, id);
                insert.setString(2, form.code());
                insert.setString(3, series);
                insert.setString(4, span(all));
                insert.setInt(5, all.size());
                insert.setString(6, received);
                insert.setString(7, note);
                insert.setString(8, now);
                insert.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO blank (form, series, number, batch_id, status, document_id, reason, changed_at) "
                    + "VALUES (?, ?, ?, ?, ?, NULL, '', ?)"
            )) {
                for (final String number : all) {
                    insert.setString(1, form.code());
                    insert.setString(2, series);
                    insert.setString(3, number);
                    insert.setString(4, id);
                    insert.setString(5, FREE);
                    insert.setString(6, now);
                    insert.executeUpdate();
                }
            }
            audit(connection, SUBJECT, id, "received", form.title() + ", серия " + series + ", № " + span(all) + " ("
                + all.size() + "), получены " + received + (note.isEmpty() ? "" : "; " + note));
            connection.commit();
        }
        return all.size();
    }

    /** Blanks of the forms there are any of, in the order of {@link Form#all}. */
    public List<Count> counts() throws SQLException {
        final Map<String, int[]> counts = new LinkedHashMap<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT form, status, COUNT(*) FROM blank GROUP BY form, status"
             );
             ResultSet row = query.executeQuery()) {
            while (row.next()) {
                final int[] count = counts.computeIfAbsent(row.getString(1), any -> new int[3]);
                count[FREE.equals(row.getString(2)) ? 0 : USED.equals(row.getString(2)) ? 1 : 2] += row.getInt(3);
            }
        }
        final List<Count> all = new ArrayList<>();
        for (final Form form : Form.all()) {
            final int[] count = counts.get(form.code());
            if (count != null) {
                all.add(new Count(form, count[0], count[1], count[2]));
            }
        }
        return all;
    }

    /** The batches, the latest first. */
    public List<Batch> batches() throws SQLException {
        final List<Batch> batches = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT * FROM blank_batch ORDER BY received DESC, created_at DESC"
             );
             ResultSet row = query.executeQuery()) {
            while (row.next()) {
                batches.add(new Batch(
                    row.getString("id"), Form.of(row.getString("form")), row.getString("series"), row.getString("numbers"),
                    row.getInt("quantity"), row.getString("received"), row.getString("note")
                ));
            }
        }
        return batches;
    }

    /** Free blanks of a form by series. */
    public List<Free> free(final Form form) throws SQLException {
        final List<Free> free = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT series, MIN(number), COUNT(*) FROM blank WHERE form = ? AND status = ? GROUP BY series "
                     + "ORDER BY series"
             )) {
            query.setString(1, form.code());
            query.setString(2, FREE);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    free.add(new Free(row.getString(1), row.getString(2), row.getInt(3)));
                }
            }
        }
        return free;
    }

    /** Every blank, by form, series and number: the register of blanks. */
    public List<Blank> all() throws SQLException {
        return this.blanks("", null);
    }

    /** The spoiled blanks, by form, series and number. */
    public List<Blank> spoiled() throws SQLException {
        return this.blanks("WHERE b.status = ?", SPOILED);
    }

    /** The blanks the documents of a graduation are printed on or were spoiled with, by document. */
    public Map<String, List<Blank>> of(final String graduationId) throws SQLException {
        final Map<String, List<Blank>> blanks = new LinkedHashMap<>();
        for (final Blank blank : this.blanks(
            "JOIN graduate g ON g.id = d.graduate_id WHERE g.graduation_id = ?", graduationId
        )) {
            blanks.computeIfAbsent(blank.documentId, any -> new ArrayList<>()).add(blank);
        }
        return blanks;
    }

    /** How the documents of a graduation were handed, by document. */
    public Map<String, Issue> issues(final String graduationId) throws SQLException {
        final Map<String, Issue> issues = new LinkedHashMap<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT i.* FROM document_issue i JOIN document d ON d.id = i.document_id "
                     + "JOIN graduate g ON g.id = d.graduate_id WHERE g.graduation_id = ?"
             )) {
            query.setString(1, graduationId);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    issues.put(row.getString("document_id"), new Issue(
                        row.getString("document_id"), row.getString("handed"), row.getString("way"),
                        row.getString("details")
                    ));
                }
            }
        }
        return issues;
    }

    /** What was done to the blanks apart from graduations, the latest first: «время — подробности». */
    public List<String> journal() throws SQLException {
        final List<String> journal = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT at, details FROM audit WHERE subject = ? ORDER BY at DESC"
             )) {
            query.setString(1, SUBJECT);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    journal.add(row.getString(1) + " — " + row.getString(2));
                }
            }
        }
        return journal;
    }

    /**
     * Writes the blanks the documents of a graduation were printed on, all
     * the runs or none.
     *
     * @return how many blanks were written
     * @throws IllegalArgumentException when a run has not enough free blanks, or a document has a blank of the kind
     */
    public int record(final String graduationId, final List<Run> runs) throws SQLException {
        int recorded = 0;
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final List<String> written = new ArrayList<>();
            for (final Run run : runs) {
                if (run.documents.isEmpty()) {
                    continue;
                }
                final List<String> numbers = take(connection, run.form, run.series, run.first, run.documents.size(), false);
                for (int number = 0; number < numbers.size(); ++number) {
                    use(connection, run.form, run.series, numbers.get(number), run.documents.get(number));
                }
                recorded += numbers.size();
                written.add(run.form.title() + ": серия " + run.series + ", № " + span(numbers) + " (" + numbers.size() + ")");
            }
            if (!written.isEmpty()) {
                Graduations.audit(connection, graduationId, BLANKS, "записаны: " + String.join("; ", written));
            }
            connection.commit();
        }
        return recorded;
    }

    /**
     * Writes the one blank a document was printed on.
     *
     * @throws IllegalArgumentException when the blank is not a free one of the form, or the document has a blank of the kind
     */
    public void record(
        final String graduationId, final String documentId, final Form form, final String series, final String number
    ) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final String taken = take(connection, form, series, number, 1, true).get(0);
            use(connection, form, series, taken, documentId);
            Graduations.audit(connection, graduationId, BLANKS, "№ " + regNumber(connection, documentId) + ": "
                + form.title() + " " + series + " " + taken);
            connection.commit();
        }
    }

    /**
     * Marks a blank spoiled: a free one in the safe, or one a document was
     * printed on and is not handed yet; the document may be printed again.
     *
     * @throws IllegalArgumentException with no reason, for a blank not there or spoiled already, or of a handed document
     */
    public void spoil(final Form form, final String series, final String number, final String reason)
        throws SQLException {
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Укажите, почему бланк испорчен: это основание для акта списания");
        }
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final Blank blank = blank(connection, form, series, number);
            if (blank == null) {
                throw new IllegalArgumentException("Бланка «" + form.title() + "» " + series + " " + number + " нет в учёте");
            }
            if (SPOILED.equals(blank.status)) {
                throw new IllegalArgumentException("Бланк " + blank.label() + " уже списан как испорченный");
            }
            if (blank.documentId != null && issued(connection, blank.documentId)) {
                throw new IllegalArgumentException("Бланк " + blank.label() + ": документ № " + blank.regNumber
                    + " выдан, бланк у выпускника; при ошибке в документе выдаётся дубликат");
            }
            try (PreparedStatement update = connection.prepareStatement(
                "UPDATE blank SET status = ?, reason = ?, changed_at = ? WHERE form = ? AND series = ? AND number = ?"
            )) {
                update.setString(1, SPOILED);
                update.setString(2, reason.strip());
                update.setString(3, Instant.now().toString());
                update.setString(4, form.code());
                update.setString(5, series);
                update.setString(6, number);
                update.executeUpdate();
            }
            final String details = form.title() + " " + blank.label() + " испорчен"
                + (blank.regNumber.isEmpty() ? "" : " (документ № " + blank.regNumber + ")") + ": " + reason.strip();
            final String graduation = blank.documentId == null ? null : graduation(connection, blank.documentId);
            if (graduation == null) {
                audit(connection, SUBJECT, form.code(), "spoiled", details);
            } else {
                Graduations.audit(connection, graduation, BLANKS, details);
            }
            connection.commit();
        }
    }

    /**
     * Writes how documents of a graduation were handed.
     *
     * @param details the number and date of the power of attorney or of the postal item; empty when handed in person
     * @throws IllegalArgumentException when a document is not on all its blanks or handed already
     */
    public void issue(
        final String graduationId, final List<String> documentIds, final String handed, final String way,
        final String details
    ) throws SQLException {
        if (!DATE.matcher(handed).matches()) {
            throw new IllegalArgumentException("Дата выдачи «" + handed + "»: нужна дата ГГГГ-ММ-ДД");
        }
        if (!WAYS.containsKey(way)) {
            throw new IllegalArgumentException("Способ выдачи «" + way + "» не известен");
        }
        if (!PERSON.equals(way) && details.isBlank()) {
            throw new IllegalArgumentException("Укажите номер и дату " + (ATTORNEY.equals(way) ? "доверенности"
                : "почтового отправления"));
        }
        final String text = PERSON.equals(way) ? "" : details.strip();
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            final List<String> numbers = new ArrayList<>();
            for (final String document : documentIds) {
                final String number = regNumber(connection, document);
                if (issued(connection, document)) {
                    throw new IllegalArgumentException("Документ № " + number + " уже выдан");
                }
                final Set<String> missing = kinds(connection, document);
                for (final Blank blank : used(connection, document)) {
                    missing.remove(blank.form.kind);
                }
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException("Документ № " + number + " не записан на бланк: "
                        + String.join(", ", titles(missing)) + ". Выдача записывается после записи бланков");
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO document_issue (document_id, handed, way, details, created_at) VALUES (?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, document);
                    insert.setString(2, handed);
                    insert.setString(3, way);
                    insert.setString(4, text);
                    insert.setString(5, Instant.now().toString());
                    insert.executeUpdate();
                }
                numbers.add(number);
            }
            if (!numbers.isEmpty()) {
                Graduations.audit(connection, graduationId, ISSUE, "выданы " + handed + " " + WAYS.get(way)
                    + (text.isEmpty() ? "" : " " + text) + ": № " + String.join(", ", numbers));
            }
            connection.commit();
        }
    }

    /** Takes back the record of handing a document entered by mistake. */
    public void cancel(final String graduationId, final String documentId) throws SQLException {
        try (Connection connection = this.database.connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM document_issue WHERE document_id = ?"
            )) {
                delete.setString(1, documentId);
                if (delete.executeUpdate() > 0) {
                    Graduations.audit(connection, graduationId, ISSUE, "№ " + regNumber(connection, documentId)
                        + ": запись о выдаче отменена");
                }
            }
            connection.commit();
        }
    }

    /** The kinds of a document: both of an original, of a duplicate what it replaces. */
    public static List<String> kinds(final boolean duplicate, final boolean diploma, final boolean supplement) {
        final List<String> kinds = new ArrayList<>();
        if (!duplicate || diploma) {
            kinds.add(Blanks.DIPLOMA);
        }
        if (!duplicate || supplement) {
            kinds.add(Blanks.SUPPLEMENT);
        }
        return kinds;
    }

    /** The documents a correction of a graduate changes when printed: names and the ГЭК are on both. */
    static Set<String> kinds(final Edits.Edit edit) {
        return edit.place == null && ON_DIPLOMA.contains(edit.field)
            ? Set.of(Blanks.DIPLOMA, Blanks.SUPPLEMENT) : Set.of(Blanks.SUPPLEMENT);
    }

    /**
     * Blanks of these kinds that documents of a graduate are printed on and
     * not handed yet: what a change of the graduate would make disagree.
     */
    static List<Blank> unhanded(final Connection connection, final String graduateId, final Set<String> kinds)
        throws SQLException {
        final List<Blank> found = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT b.*, d.reg_number, '' AS received FROM blank b JOIN document d ON d.id = b.document_id "
                + "WHERE d.graduate_id = ? AND b.status = ? "
                + "AND NOT EXISTS (SELECT 1 FROM document_issue i WHERE i.document_id = d.id) ORDER BY b.number"
        )) {
            query.setString(1, graduateId);
            query.setString(2, USED);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    final Blank blank = blank(row);
                    if (kinds.contains(blank.form.kind)) {
                        found.add(blank);
                    }
                }
            }
        }
        return found;
    }

    /** The blanks a document is printed on. */
    static List<Blank> used(final Connection connection, final String documentId) throws SQLException {
        final List<Blank> used = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT b.*, d.reg_number, '' AS received FROM blank b JOIN document d ON d.id = b.document_id "
                + "WHERE b.document_id = ? AND b.status = ?"
        )) {
            query.setString(1, documentId);
            query.setString(2, USED);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    used.add(blank(row));
                }
            }
        }
        return used;
    }

    static boolean issued(final Connection connection, final String documentId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT 1 FROM document_issue WHERE document_id = ?"
        )) {
            query.setString(1, documentId);
            try (ResultSet row = query.executeQuery()) {
                return row.next();
            }
        }
    }

    /** How many blanks the documents of a graduation are printed on. */
    static int printed(final Connection connection, final String graduationId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT COUNT(*) FROM blank b JOIN document d ON d.id = b.document_id JOIN graduate g ON g.id = d.graduate_id "
                + "WHERE g.graduation_id = ? AND b.status = ?"
        )) {
            query.setString(1, graduationId);
            query.setString(2, USED);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        }
    }

    /** «Диплом бакалавра 123456 0000101, …» of blanks. */
    static String labels(final List<Blank> blanks) {
        final List<String> labels = new ArrayList<>();
        for (final Blank blank : blanks) {
            labels.add(blank.form.title() + " " + blank.label());
        }
        return String.join(", ", labels);
    }

    /** Numbers in the order given, the runs of consecutive ones joined: «0000101–0000105, 0000110». */
    static String span(final List<String> numbers) {
        final List<String> parts = new ArrayList<>();
        int start = 0;
        for (int at = 1; at <= numbers.size(); ++at) {
            if (at < numbers.size() && next(numbers.get(at - 1), numbers.get(at))) {
                continue;
            }
            parts.add(at - 1 == start ? numbers.get(start) : numbers.get(start) + "–" + numbers.get(at - 1));
            start = at;
        }
        return String.join(", ", parts);
    }

    private static boolean next(final String one, final String other) {
        return one.length() == other.length() && one.chars().allMatch(Character::isDigit)
            && other.chars().allMatch(Character::isDigit) && Long.parseLong(other) == Long.parseLong(one) + 1;
    }

    /**
     * The free blanks of a form and series a run takes: from the first on,
     * skipping those used or spoiled already; exactly the first when asked.
     */
    private static List<String> take(
        final Connection connection, final Form form, final String series, final String first, final int count,
        final boolean exact
    ) throws SQLException {
        if (!SERIES.matcher(series).matches() || !NUMBER.matcher(first).matches()) {
            throw new IllegalArgumentException("Бланк «" + series + " " + first + "»: серия — шесть цифр, номер — семь");
        }
        if (exact) {
            final Blank blank = blank(connection, form, series, first);
            if (blank == null) {
                throw new IllegalArgumentException("Бланка «" + form.title() + "» " + series + " " + first
                    + " нет в учёте: внесите приход на странице «Бланки строгой отчётности»");
            }
            if (!FREE.equals(blank.status)) {
                throw new IllegalArgumentException("Бланк " + blank.label() + " " + blank.state()
                    + (blank.regNumber.isEmpty() ? "" : " (документ № " + blank.regNumber + ")"));
            }
            return List.of(first);
        }
        final List<String> numbers = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT number FROM blank WHERE form = ? AND series = ? AND status = ? AND number >= ? ORDER BY number LIMIT ?"
        )) {
            query.setString(1, form.code());
            query.setString(2, series);
            query.setString(3, FREE);
            query.setString(4, first);
            query.setInt(5, count);
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    numbers.add(row.getString(1));
                }
            }
        }
        if (numbers.size() < count) {
            throw new IllegalArgumentException("Бланков «" + form.title() + "» серии " + series + " с № " + first
                + " свободно " + numbers.size() + ", а документов " + count + ": внесите приход или укажите другой первый бланк");
        }
        return numbers;
    }

    /** Puts a document on a blank; a document has one blank of a kind. */
    private static void use(
        final Connection connection, final Form form, final String series, final String number, final String documentId
    ) throws SQLException {
        for (final Blank blank : used(connection, documentId)) {
            if (blank.form.kind.equals(form.kind)) {
                throw new IllegalArgumentException("Документ № " + blank.regNumber + " уже записан на бланк "
                    + blank.label() + "; если тот испорчен, отметьте это в карточке выпускника");
            }
        }
        if (!kinds(connection, documentId).contains(form.kind)) {
            throw new IllegalArgumentException("Документ № " + regNumber(connection, documentId) + " — дубликат без «"
                + Blanks.KINDS.get(form.kind) + "»");
        }
        try (PreparedStatement update = connection.prepareStatement(
            "UPDATE blank SET status = ?, document_id = ?, changed_at = ? WHERE form = ? AND series = ? AND number = ? "
                + "AND status = ?"
        )) {
            update.setString(1, USED);
            update.setString(2, documentId);
            update.setString(3, Instant.now().toString());
            update.setString(4, form.code());
            update.setString(5, series);
            update.setString(6, number);
            update.setString(7, FREE);
            if (update.executeUpdate() != 1) {
                throw new IllegalArgumentException("Бланк " + series + " " + number + " уже не свободен");
            }
        }
    }

    private List<Blank> blanks(final String where, final String parameter) throws SQLException {
        final List<Blank> blanks = new ArrayList<>();
        try (Connection connection = this.database.connection();
             PreparedStatement query = connection.prepareStatement(
                 "SELECT b.*, d.reg_number, p.received FROM blank b JOIN blank_batch p ON p.id = b.batch_id "
                     + (where.contains("graduate g") ? "JOIN" : "LEFT JOIN") + " document d ON d.id = b.document_id "
                     + where + " ORDER BY b.form, b.series, b.number"
             )) {
            if (parameter != null) {
                query.setString(1, parameter);
            }
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    blanks.add(blank(row));
                }
            }
        }
        return blanks;
    }

    private static Blank blank(final Connection connection, final Form form, final String series, final String number)
        throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT b.*, d.reg_number, '' AS received FROM blank b LEFT JOIN document d ON d.id = b.document_id "
                + "WHERE b.form = ? AND b.series = ? AND b.number = ?"
        )) {
            query.setString(1, form.code());
            query.setString(2, series);
            query.setString(3, number);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? blank(row) : null;
            }
        }
    }

    private static Blank blank(final ResultSet row) throws SQLException {
        return new Blank(
            Form.of(row.getString("form")), row.getString("series"), row.getString("number"), row.getString("status"),
            row.getString("document_id"), row.getString("reg_number"), row.getString("reason"),
            row.getString("changed_at"), row.getString("received")
        );
    }

    /** The kinds a document is printed on, from the registry. */
    private static Set<String> kinds(final Connection connection, final String documentId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT duplicate_of, diploma_duplicate, supplement_duplicate FROM document WHERE id = ?"
        )) {
            query.setString(1, documentId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new IllegalArgumentException("Документа нет в картотеке");
                }
                return new LinkedHashSet<>(kinds(
                    row.getString(1) != null, row.getInt(2) == 1, row.getInt(3) == 1
                ));
            }
        }
    }

    private static String regNumber(final Connection connection, final String documentId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT reg_number FROM document WHERE id = ?")) {
            query.setString(1, documentId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? Objects.toString(row.getString(1), "—") : "—";
            }
        }
    }

    private static String graduation(final Connection connection, final String documentId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT g.graduation_id FROM document d JOIN graduate g ON g.id = d.graduate_id WHERE d.id = ?"
        )) {
            query.setString(1, documentId);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static List<String> titles(final Set<String> kinds) {
        final List<String> titles = new ArrayList<>();
        for (final String kind : kinds) {
            titles.add(Blanks.KINDS.get(kind).toLowerCase(Locale.ROOT));
        }
        return titles;
    }

    private static void audit(
        final Connection connection, final String subject, final String id, final String action, final String details
    ) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
            "INSERT INTO audit (id, at, subject, subject_id, action, details) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, Instant.now().toString());
            insert.setString(3, subject);
            insert.setString(4, id);
            insert.setString(5, action);
            insert.setString(6, details.length() > 4000 ? details.substring(0, 4000) : details);
            insert.executeUpdate();
        }
    }
}
