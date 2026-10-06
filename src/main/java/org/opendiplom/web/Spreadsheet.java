package org.opendiplom.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * A table as an XLSX workbook to print on A4 landscape: a title, the header
 * on every page, the pages numbered.
 */
final class Spreadsheet {
    private Spreadsheet() {
    }

    /**
     * @param widths of the columns in characters
     */
    static byte[] of(final String sheet, final String title, final String[] headers, final int[] widths,
        final List<String[]> rows) throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final XSSFSheet table = book.createSheet(sheet);
            final CellStyle caption = style(book, true, false);
            final CellStyle head = style(book, true, true);
            head.setAlignment(HorizontalAlignment.CENTER);
            final CellStyle plain = style(book, false, true);
            final Cell name = table.createRow(0).createCell(0);
            name.setCellValue(title);
            name.setCellStyle(caption);
            table.addMergedRegion(new CellRangeAddress(0, 0, 0, headers.length - 1));
            final Row header = table.createRow(1);
            for (int column = 0; column < headers.length; ++column) {
                final Cell cell = header.createCell(column);
                cell.setCellValue(headers[column]);
                cell.setCellStyle(head);
                table.setColumnWidth(column, widths[column] * 256);
            }
            int at = 2;
            for (final String[] values : rows) {
                final Row row = table.createRow(at++);
                for (int column = 0; column < headers.length; ++column) {
                    final Cell cell = row.createCell(column);
                    cell.setCellValue(column < values.length && values[column] != null ? values[column] : "");
                    cell.setCellStyle(plain);
                }
            }
            final PrintSetup print = table.getPrintSetup();
            print.setLandscape(true);
            print.setPaperSize(PrintSetup.A4_PAPERSIZE);
            print.setFitWidth((short) 1);
            print.setFitHeight((short) 0);
            table.setFitToPage(true);
            table.setRepeatingRows(CellRangeAddress.valueOf("2:2"));
            table.getFooter().setCenter("Лист &P");
            table.createFreezePane(0, 2);
            book.write(out);
            return out.toByteArray();
        }
    }

    private static CellStyle style(final XSSFWorkbook book, final boolean bold, final boolean framed) {
        final Font font = book.createFont();
        font.setFontName("Times New Roman");
        font.setFontHeightInPoints((short) (framed ? 10 : 12));
        font.setBold(bold);
        final CellStyle style = book.createCellStyle();
        style.setFont(font);
        style.setWrapText(framed);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        if (framed) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
        return style;
    }
}
