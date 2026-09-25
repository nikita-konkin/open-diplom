package org.opendiplom.sheets;

import java.util.Collections;
import java.util.List;

/**
 * Cell values of one worksheet, row by row from the first sheet row.
 *
 * <p>A value is a {@link String}, a {@link Double}, a {@link Boolean},
 * a {@link java.time.LocalDateTime} for cells formatted as dates, or
 * {@code null} for an empty cell. Rows are not padded: a short row simply
 * has fewer values.
 */
public final class Sheet {
    private final String name;
    private final List<List<Object>> rows;

    public Sheet(final String name, final List<List<Object>> rows) {
        this.name = name;
        this.rows = Collections.unmodifiableList(rows);
    }

    public String name() {
        return this.name;
    }

    public List<List<Object>> rows() {
        return this.rows;
    }

    /** Number of columns: the length of the longest row. */
    public int width() {
        int width = 0;
        for (final List<Object> row : this.rows) {
            width = Math.max(width, row.size());
        }
        return width;
    }

    /** Value at a 0-based row and column, {@code null} outside the data. */
    public Object cell(final int row, final int column) {
        if (row < 0 || row >= this.rows.size()) {
            return null;
        }
        final List<Object> values = this.rows.get(row);
        return column >= 0 && column < values.size() ? values.get(column) : null;
    }
}
