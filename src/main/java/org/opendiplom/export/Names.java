package org.opendiplom.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.opendiplom.sheets.Cells;

/** Matching of people by name, tolerant to ё, case and Latin lookalike letters. */
final class Names {
    private static final String LATIN = "AaBCcEeHKMOoPpTXxYyËë";
    private static final String CYRILLIC = "АаВСсЕеНКМОоРрТХхУуЁё";
    private static final Pattern MIXED_SCRIPT = Pattern.compile(
        "\\b(?=\\w*[А-Яа-яЁё])(?=\\w*[A-Za-zÀ-ÿ])\\w+\\b", Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern LETTERS = Pattern.compile(
        "[^\\W\\d_]+", Pattern.UNICODE_CHARACTER_CLASS
    );

    private Names() {
    }

    /** Case-, ё- and lookalike-insensitive form of a name. */
    static String key(final Object text) {
        final StringBuilder name = new StringBuilder();
        for (final char letter : Cells.raw(text).toCharArray()) {
            final int lookalike = LATIN.indexOf(letter);
            name.append(lookalike >= 0 ? CYRILLIC.charAt(lookalike) : letter);
        }
        return Cells.collapse(name.toString().toLowerCase(Locale.ROOT).replace('ё', 'е'));
    }

    /** Words mixing Cyrillic and Latin letters, e.g. a Latin Ë in a surname. */
    static List<String> mixedScript(final String text) {
        final List<String> words = new ArrayList<>();
        final Matcher match = MIXED_SCRIPT.matcher(text);
        while (match.find()) {
            words.add(match.group());
        }
        return words;
    }

    /** Surname plus first-name and patronymic initials. */
    static List<String> person(final String last, final String first, final String middle) {
        return Arrays.asList(key(last), head(key(first)), head(key(middle)));
    }

    /** Key of a pivot column headed «Фамилия И. О.». */
    static List<String> column(final Object header) {
        final String[] words = key(header).split(" ", 2);
        final List<String> initials = new ArrayList<>();
        if (words.length > 1) {
            final Matcher match = LETTERS.matcher(words[1]);
            while (match.find()) {
                initials.add(match.group().substring(0, 1));
            }
        }
        return Arrays.asList(
            words[0],
            initials.isEmpty() ? "" : initials.get(0),
            initials.size() > 1 ? initials.get(1) : ""
        );
    }

    /** Same surname; an initial missing on either side matches anything. */
    static boolean compatible(final List<String> column, final List<String> person) {
        if (!column.get(0).equals(person.get(0))) {
            return false;
        }
        for (int index = 1; index < 3; ++index) {
            final String a = column.get(index);
            final String b = person.get(index);
            if (!a.equals(b) && !a.isEmpty() && !b.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String head(final String text) {
        return text.isEmpty() ? "" : text.substring(0, 1);
    }
}
