package org.opendiplom.imports;

import java.util.Collections;
import java.util.List;

/** A row of the table «План учебного процесса», from a workbook or a PDF. */
final class PlanRow {
    /** Index of an element («Б.1.1.1», «1» of a facultative); empty for blocks, headings and totals. */
    final String index;
    /** Name of an element, or the text of a block, heading or total. */
    final String name;
    /** «Объем частей ОП в зачетных единицах» → «Всего». */
    final Double credits;
    /**
     * «Объем частей ОП в часах» column by column: всего, экзамены, учебные
     * занятия, контактная работа, самостоятельная работа; a missing value is
     * {@code null}, a list is empty when the hours were not read.
     */
    final List<Double> hours;

    PlanRow(final String index, final String name, final Double credits, final List<Double> hours) {
        this.index = index;
        this.name = name;
        this.credits = credits;
        this.hours = Collections.unmodifiableList(hours);
    }
}
