package org.opendiplom.printing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Documents printed on their blanks: the template filled with the data of
 * each document and laid out, all of them in one PDF in the order given.
 */
public final class BlankPrint {
    private BlankPrint() {
    }

    /**
     * @param regular TrueType font with Cyrillic, metrics of Times New Roman
     * @param bold its bold face, {@code null} to thicken the regular one
     * @param sample whether to mark every page «ОБРАЗЕЦ»; a sample ends with a page of the problems
     * @param problems where fields the data has not and texts that do not fit are reported
     */
    public static byte[] pdf(
        final BlankTemplate template, final List<BlankData> documents, final Path regular, final Path bold,
        final Calibration calibration, final boolean sample, final Set<String> problems
    ) throws IOException {
        try (BlankPdf pdf = new BlankPdf(regular, bold, calibration)) {
            for (final BlankData data : documents) {
                pdf.add(BlankLayout.of(template, data, pdf.measure(), problems), sample);
            }
            if (sample && !problems.isEmpty()) {
                pdf.notes("Замечания к образцу", new ArrayList<>(problems));
            }
            return pdf.pdf();
        }
    }
}
