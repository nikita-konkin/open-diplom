package org.opendiplom.printing;

/**
 * How far a printer shifts the page: millimetres to the right and down.
 * Measured once per printer on the test sheet, then added to every field.
 */
public final class Calibration {
    public static final Calibration NONE = new Calibration(0, 0);
    /** Larger shifts mean a wrong paper size or scaling, not a calibration. */
    private static final float LIMIT = 10;

    private final float dx;
    private final float dy;

    public Calibration(final float dx, final float dy) {
        if (Math.abs(dx) > LIMIT || Math.abs(dy) > LIMIT) {
            throw new IllegalArgumentException(
                "Поправка больше 10 мм: проверьте, что печать идёт в масштабе 100% на нужный формат листа"
            );
        }
        this.dx = dx;
        this.dy = dy;
    }

    public float dx() {
        return this.dx;
    }

    public float dy() {
        return this.dy;
    }
}
