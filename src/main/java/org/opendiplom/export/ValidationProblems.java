package org.opendiplom.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Problems in the input data, reported together rather than one at a time. */
public final class ValidationProblems extends Exception {
    private static final long serialVersionUID = 1L;

    private final List<String> problems;

    public ValidationProblems(final List<String> problems) {
        super(message(problems));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
    }

    public List<String> problems() {
        return this.problems;
    }

    private static String message(final List<String> problems) {
        final StringBuilder text = new StringBuilder("Найдено ошибок в исходных данных: ")
            .append(problems.size());
        for (final String problem : problems) {
            text.append("\n- ").append(problem);
        }
        return text.toString();
    }
}
