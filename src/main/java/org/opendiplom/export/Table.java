package org.opendiplom.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opendiplom.sheets.Cells;
import org.opendiplom.sheets.Sheet;

/**
 * A sheet whose first row holds column titles, read the way pandas reads it:
 * an empty title becomes «Unnamed: N», a repeated one gets «.1», «.2».
 */
final class Table {
    final List<Object> columns;
    /** Data rows with their 1-based sheet row numbers. */
    final List<Integer> lines = new ArrayList<>();
    final List<List<Object>> rows = new ArrayList<>();

    Table(final Sheet sheet) {
        final int width = sheet.width();
        this.columns = new ArrayList<>();
        final Map<String, Integer> seen = new HashMap<>();
        for (int column = 0; column < width; ++column) {
            final Object title = sheet.cell(0, column);
            if (Cells.missing(title)) {
                this.columns.add("Unnamed: " + column);
            } else if (title instanceof String) {
                final String text = (String) title;
                final int count = seen.getOrDefault(text, 0);
                seen.put(text, count + 1);
                this.columns.add(count == 0 ? text : text + '.' + count);
            } else {
                this.columns.add(title);
            }
        }
        for (int row = 1; row < sheet.rows().size(); ++row) {
            final List<Object> values = new ArrayList<>();
            for (int column = 0; column < width; ++column) {
                values.add(sheet.cell(row, column));
            }
            this.lines.add(row + 1);
            this.rows.add(values);
        }
    }

    int column(final String title) {
        return this.columns.indexOf(title);
    }
}
