package org.opendiplom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Synthetic workbooks in the layouts the program reads; no real data in tests. */
public final class Books {
    public static final List<String> TITLES =
        Arrays.asList("наименование предмета", "часы учр", "зачет", "экзамен", "курсовой");

    private Books() {
    }

    /** One student of a statement: name for E1 and rows (subject, hours, зачет, экзамен, курсовой). */
    public static Object[] student(final String name, final Object[]... rows) {
        return new Object[] {name, rows};
    }

    public static Object[] row(final Object... values) {
        return values;
    }

    /** «Деканат» statement in .xlsx: a sheet per student, name in E1, titles on row 7. */
    public static byte[] statement(final Object[]... students) {
        return statement(new XSSFWorkbook(), TITLES, students);
    }

    /** The same statement in the old .xls format. */
    public static byte[] oldStatement(final Object[]... students) {
        return statement(new HSSFWorkbook(), TITLES, students);
    }

    public static byte[] statement(final Workbook book, final List<String> titles, final Object[]... students) {
        for (int number = 0; number < students.length; ++number) {
            final Sheet sheet = book.createSheet("Лист" + (number + 1));
            put(sheet, 0, "", "", "", "", students[number][0]);
            for (int filler = 1; filler < 6; ++filler) {
                put(sheet, filler, "-");
            }
            put(sheet, 6, titles.toArray());
            int line = 7;
            for (final Object[] values : (Object[][]) students[number][1]) {
                put(sheet, line++, values);
            }
        }
        return save(book);
    }

    /** Curriculum: a header, a sub-header «Всего» and a row per element. */
    public static byte[] curriculum(final Map<String, Object> credits, final String name, final String credit) {
        final Workbook book = new XSSFWorkbook();
        final Sheet sheet = book.createSheet("Основная часть");
        put(sheet, 0, "№", name, "Кафедра", credit);
        put(sheet, 1, "", "", "", "Всего");
        int line = 2;
        for (final Map.Entry<String, Object> entry : credits.entrySet()) {
            put(sheet, line, "Б.1.1." + (line - 1), entry.getKey(), "Кафедра", entry.getValue());
            ++line;
        }
        return save(book);
    }

    public static byte[] curriculum(final Map<String, Object> credits) {
        return curriculum(credits, "Структура ОП", "Объем частей ОП\nв зачетных единицах");
    }

    /** Any sheet from rows of values; dates become date-formatted cells. */
    public static byte[] book(final Object[]... rows) {
        final Workbook book = new XSSFWorkbook();
        final Sheet sheet = book.createSheet("Лист1");
        for (int line = 0; line < rows.length; ++line) {
            put(sheet, line, rows[line]);
        }
        return save(book);
    }

    private static void put(final Sheet sheet, final int line, final Object... values) {
        final Row row = sheet.createRow(line);
        for (int column = 0; column < values.length; ++column) {
            final Object value = values[column];
            if (value == null) {
                continue;
            }
            final Cell cell = row.createCell(column);
            if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value instanceof LocalDateTime) {
                final CellStyle style = sheet.getWorkbook().createCellStyle();
                style.setDataFormat((short) 14);
                cell.setCellStyle(style);
                cell.setCellValue((LocalDateTime) value);
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    private static byte[] save(final Workbook book) {
        try (Workbook closing = book; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            closing.write(out);
            return out.toByteArray();
        } catch (final IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
