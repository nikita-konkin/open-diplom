package org.opendiplom.sheets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads uploaded workbooks in both the .xlsx and the old .xls format.
 *
 * <p>The format is chosen by the file signature, not by the file name:
 * departments rename files freely.
 */
public final class Workbooks {
    private static final byte[] XLSX = {0x50, 0x4b, 0x03, 0x04};
    private static final byte[] XLS = {
        (byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
        (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1,
    };

    /** Texts that pandas reads as an empty cell; kept so both readers agree. */
    private static final Set<String> EMPTY_TEXTS = new HashSet<>(Arrays.asList(
        "", "#N/A", "#N/A N/A", "#NA", "-1.#IND", "-1.#QNAN", "-NaN", "-nan",
        "1.#IND", "1.#QNAN", "<NA>", "N/A", "NA", "NULL", "NaN", "None", "n/a",
        "nan", "null"
    ));

    private Workbooks() {
    }

    /**
     * All sheets of a workbook, in workbook order.
     *
     * @param content uploaded bytes
     * @param title what the file is, for messages («Ведомость»)
     * @throws WorkbookException the bytes are not a readable workbook
     */
    public static List<Sheet> read(final byte[] content, final String title)
        throws WorkbookException {
        final boolean xlsx = startsWith(content, XLSX);
        if (!xlsx && !startsWith(content, XLS)) {
            throw new WorkbookException(
                title + ": файл не является книгой Excel (.xlsx или .xls). "
                    + "Откройте его в Excel и сохраните заново."
            );
        }
        try (Workbook book = xlsx
            ? new XSSFWorkbook(new ByteArrayInputStream(content))
            : new HSSFWorkbook(new ByteArrayInputStream(content))) {
            final List<Sheet> sheets = new ArrayList<>();
            for (int index = 0; index < book.getNumberOfSheets(); ++index) {
                sheets.add(sheet(book.getSheetAt(index)));
            }
            return sheets;
        } catch (final IOException | RuntimeException error) {
            throw new WorkbookException(
                title + ": не удалось открыть книгу Excel (" + error.getMessage()
                    + "). Откройте её в Excel и сохраните заново.",
                error
            );
        }
    }

    private static Sheet sheet(final org.apache.poi.ss.usermodel.Sheet source) {
        final List<List<Object>> rows = new ArrayList<>();
        for (int number = 0; number <= source.getLastRowNum(); ++number) {
            final Row row = source.getRow(number);
            final List<Object> values = new ArrayList<>();
            if (row != null) {
                for (int column = 0; column < Math.max(row.getLastCellNum(), 0); ++column) {
                    values.add(value(row.getCell(column)));
                }
            }
            while (!values.isEmpty() && values.get(values.size() - 1) == null) {
                values.remove(values.size() - 1);
            }
            rows.add(values);
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
            rows.remove(rows.size() - 1);
        }
        return new Sheet(source.getSheetName(), rows);
    }

    private static Object value(final Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case STRING:
                return text(cell.getStringCellValue());
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue();
                }
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case ERROR:
                return text(FormulaError.forInt(cell.getErrorCellValue()).getString());
            default:
                return null;
        }
    }

    private static String text(final String value) {
        return EMPTY_TEXTS.contains(value) ? null : value;
    }

    private static boolean startsWith(final byte[] content, final byte[] prefix) {
        return content != null && content.length >= prefix.length
            && Arrays.equals(Arrays.copyOf(content, prefix.length), prefix);
    }
}
