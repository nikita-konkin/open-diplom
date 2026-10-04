package org.opendiplom.catalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registration numbers as the operator writes them: «10001–10007, 10010».
 * The university gives them in a row or with gaps, so a list may have both.
 */
public final class Numbers {
    /** More in one range is a typo rather than a graduation. */
    private static final int LONGEST = 1000;
    private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*[-–—]\\s*(\\d+)");
    private static final Pattern SEPARATOR = Pattern.compile("[,;\\s]+");

    private Numbers() {
    }

    /**
     * The numbers in the order written.
     *
     * @throws IllegalArgumentException on a range backwards or too long, or a number twice
     */
    public static List<String> parse(final String text) {
        final Set<String> numbers = new LinkedHashSet<>();
        final String joined = RANGE.matcher(text == null ? "" : text).replaceAll("$1–$2");
        for (final String token : SEPARATOR.split(joined.strip())) {
            if (token.isEmpty()) {
                continue;
            }
            final Matcher range = RANGE.matcher(token);
            final List<String> found = new ArrayList<>();
            if (range.matches()) {
                final long first = Long.parseLong(range.group(1));
                final long last = Long.parseLong(range.group(2));
                if (last < first || last - first >= LONGEST) {
                    throw new IllegalArgumentException("Диапазон «" + token + "»: начало больше конца или номеров больше "
                        + LONGEST);
                }
                final int width = range.group(1).length();
                for (long number = first; number <= last; ++number) {
                    found.add(String.format("%0" + width + "d", number));
                }
            } else {
                found.add(token);
            }
            for (final String number : found) {
                if (!numbers.add(number)) {
                    throw new IllegalArgumentException("Номер " + number + " записан дважды");
                }
            }
        }
        return new ArrayList<>(numbers);
    }
}
