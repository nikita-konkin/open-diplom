package org.opendiplom.imports;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Credits (з.е.) and the labels that carry them. */
public final class Credits {
    public static final int HOURS_PER_CREDIT = 36;

    /** Label «Название_тип_з.е.». */
    public static final Pattern LABEL = Pattern.compile(
        "^(.*)_(дисциплина|практика|факультатив|курсовая)_(\\d+)$"
    );

    private Credits() {
    }

    /** Credits for hours, rounded: 106 hours of a 3-credit discipline are 3. */
    public static int ofHours(final double hours) {
        return (int) (hours / HOURS_PER_CREDIT + 0.5);
    }

    /** Name part of a label, or {@code null} when the text is not a label. */
    public static String nameOf(final String label) {
        final Matcher match = LABEL.matcher(label);
        return match.matches() ? match.group(1) : null;
    }
}
