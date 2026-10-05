package org.opendiplom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * FastReport templates made up for the tests, laid out as the CyberDiploma
 * ones are: a supplement of two A3 sheets in two columns, the table of
 * modules with its script, the additional information in a subreport. The
 * positions are round numbers, not those of a typographer's template
 * (ADR-0003).
 */
public final class Templates {
    /** The line spacing the CyberDiploma scripts give to the rows of the table. */
    private static final String SCRIPT = "procedure Page2OnBeforePrint(Sender: TfrxComponent);\r\nbegin\r\n"
        + "  with memName do\r\n  begin\r\n    LineSpacing := 1.5;\r\n    memHours.LineSpacing := LineSpacing;\r\n"
        + "    memRating.LineSpacing := LineSpacing;\r\n  end;\r\nend;\r\n"
        + "procedure Page1OnBeforePrint(Sender: TfrxComponent);\r\nbegin\r\n  with memNameNS do\r\n  begin\r\n"
        + "    LineSpacing := 1.5;\r\n  end;\r\nend;\r\nbegin\r\nend.";
    private static final String A3 = "Orientation=\"poLandscape\" PaperWidth=\"420\" PaperHeight=\"297\" LeftMargin=\"0\" "
        + "TopMargin=\"0\" RightMargin=\"0\" BottomMargin=\"0\" Columns=\"2\" ColumnWidth=\"210\"";

    private Templates() {
    }

    /**
     * The supplement: on the first sheet the surname, the registration number and the additional
     * information; on the second the table, its rows 16 pixels a line, after a header of 189
     * pixels and, in the first column, a spacer of 38. The field of the names of the table is
     * 414.6 pixels wide inside its gaps.
     */
    public static byte[] supplement() {
        return xml(
            page("Page1", A3 + " ColumnPositions.Text=\"0&#13;&#10;210\"",
                memo("memLast", "1150", "200", "280", "26", "-24", "WordWrap=\"False\" GapX=\"0\" GapY=\"0\"",
                    "[Студент.&#34;Фамилия&#34;]")
                    + memo("memNumber", "800", "840", "230", "19", "-15", "HAlign=\"haCenter\"",
                        "[Документ.&#34;Регистрационный номер&#34;]")
                    + "<TfrxSubreport Name=\"NS\" Left=\"48\" Top=\"125\" Width=\"660\" Height=\"117\" Page=\"Page3\"/>")
            + page("Page2", A3 + " ColumnPositions.Text=\"0&#13;&#10;209\" OnBeforePrint=\"Page2OnBeforePrint\"",
                "<TfrxPageFooter Name=\"Footer\" Left=\"0\" Top=\"400\" Width=\"1587\" Height=\"47\">"
                    + memo("memPage2", "130", "8", "12", "19", "-15", "", "2") + "</TfrxPageFooter>"
                    + "<TfrxPageHeader Name=\"Header\" Left=\"0\" Top=\"19\" Width=\"1587\" Height=\"189\"/>"
                    + "<TfrxMasterData Name=\"bndTable\" Left=\"0\" Top=\"321\" Width=\"794\" Height=\"22,5\" RowCount=\"0\" "
                    + "Stretched=\"True\" OnAfterCalcHeight=\"OnAfterCalcHeight\">"
                    + memo("memName", "57", "0", "418,6", "15", "-15", "StretchMode=\"smActualHeight\" GapY=\"-1\" LineSpacing=\"0\"",
                        "[Модули и разделы.&#34;Наименование&#34;]")
                    + memo("memHours", "478", "0", "150", "15", "-15", "HAlign=\"haCenter\" StretchMode=\"smActualHeight\" "
                        + "WordWrap=\"False\" GapY=\"-1\"", "[Модули и разделы.&#34;Трудоёмкость&#34;]")
                    + memo("memRating", "635", "0", "132", "15", "-15", "HAlign=\"haCenter\" StretchMode=\"smActualHeight\" "
                        + "WordWrap=\"False\" AutoWidth=\"True\" GapY=\"-1\"", "[Модули и разделы.&#34;Оценка&#34;]")
                    + "</TfrxMasterData>"
                    + "<TfrxMasterData Name=\"MasterData1\" Left=\"0\" Top=\"257\" Width=\"794\" Height=\"38\" RowCount=\"1\"/>")
            + page("Page3", A3,
                "<TfrxMasterData Name=\"bndNS\" Left=\"0\" Top=\"19\" Width=\"794\" Height=\"22\" RowCount=\"0\" "
                    + "Stretched=\"True\" OnAfterCalcHeight=\"OnAfterCalcHeightNS\">"
                    + memo("memNameNS", "0", "0", "690", "19", "-15", "StretchMode=\"smActualHeight\" GapY=\"-1\"",
                        "[Дополнительные сведения.&#34;Наименование&#34;]")
                    + "</TfrxMasterData>")
        );
    }

    /** A diploma: one A4 sheet with fields only, the number centred, the name of the organization centred down. */
    public static byte[] diploma() {
        return xml(page("Document", "Orientation=\"poLandscape\" PaperWidth=\"297\" PaperHeight=\"210\" LeftMargin=\"7\" "
                + "TopMargin=\"0,2\" RightMargin=\"0\" BottomMargin=\"0\"",
            memo("memNumber", "26", "645", "495", "19", "-15", "HAlign=\"haCenter\" GapX=\"0\" GapY=\"-2\"",
                "[Документ.&#34;Регистрационный номер&#34;]")
                + memo("memCollege", "26", "220", "495", "113", "-15", "HAlign=\"haCenter\" VAlign=\"vaCenter\"",
                    "[Образовательное учреждение.&#34;Наименование&#34;]&#13;&#10;"
                    + "[Образовательное учреждение.&#34;Местонахождение&#34;]")
                + memo("memDuplicate", "30", "317", "495", "34", "-21", "HAlign=\"haCenter\" Font.Style=\"1\"",
                    "[IIF(&#60;Документ.&#34;Диплом: дубликат&#34;&#62;=1,'ДУБЛИКАТ','')]")
                + memo("memChairman", "900", "635", "155", "19", "-15", "HAlign=\"haRight\"",
                    "[Студент.&#34;Председатель: Фамилия&#34;] [Copy(&#60;Студент.&#34;Председатель: Имя&#34;&#62;,1,1)]."
                        + "[Copy(&#60;Студент.&#34;Председатель: Отчество&#34;&#62;,1,1)].")
                + memo("memDay", "839", "580", "42", "19", "-15", "HAlign=\"haCenter\"",
                    "[Студент.&#34;Дата решения госкомиссии&#34; #Ddd]")
                + memo("memYear", "1020", "580", "23", "19", "-15", "HAlign=\"haCenter\"",
                    "[Copy(IntToStr(YearOf(&#60;Студент.&#34;Дата решения госкомиссии&#34;&#62;)),3,2)]")));
    }

    /** As CyberDiploma keeps its templates. */
    public static byte[] gzip(final byte[] content) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(content);
        } catch (final IOException error) {
            throw new UncheckedIOException(error);
        }
        return out.toByteArray();
    }

    /** A report of pages; the script is that of the supplement. */
    public static byte[] xml(final String pages) {
        return ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<TfrxReport Version=\"4.9.72\" ScriptLanguage=\"PascalScript\" "
            + "ScriptText.Text=\"" + SCRIPT.replace("\r\n", "&#13;&#10;") + "\">"
            + "<TfrxDataPage Name=\"Data\" Height=\"1000\" Left=\"0\" Top=\"0\" Width=\"1000\"/>" + pages
            + "</TfrxReport>").getBytes(StandardCharsets.UTF_8);
    }

    public static String page(final String name, final String attributes, final String content) {
        return "<TfrxReportPage Name=\"" + name + "\" " + attributes + ">" + content + "</TfrxReportPage>";
    }

    /** A text field; positions in pixels, the font height negative as FastReport writes it. */
    public static String memo(
        final String name, final String left, final String top, final String width, final String height,
        final String font, final String attributes, final String text
    ) {
        return "<TfrxMemoView Name=\"" + name + "\" Left=\"" + left + "\" Top=\"" + top + "\" Width=\"" + width
            + "\" Height=\"" + height + "\" Font.Height=\"" + font + "\" Font.Name=\"Times New Roman\" " + attributes
            + " Text=\"" + text + "\"/>";
    }
}
