package org.opendiplom.export;

import org.opendiplom.sheets.Cells;

/**
 * CyberDiploma grade codes: 2–5 are marks, 6 is «зачтено», 7 is «не выполнял»,
 * a placeholder used until the real grade is known (D-01).
 */
final class Grades {
    private Grades() {
    }

    /** The grade code, or {@code null} when the value is not one. */
    static Integer code(final Object value) {
        final Double number = value instanceof Boolean ? Double.valueOf((Boolean) value ? 1 : 0)
            : Cells.number(value);
        if (number == null || number != Math.rint(number) || number < 2 || number > 7) {
            return null;
        }
        return number.intValue();
    }
}
