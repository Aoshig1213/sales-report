package io.github.aoshig1213.salesreport.writer;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 帳票で使うセルの書式。ブックごとに1回だけ作る（セルごとに作るとファイルが重くなり、書式数の上限にも当たる）。
 */
final class Styles {

    private static final String FONT_NAME = "游ゴシック";
    private static final String NUMBER_FORMAT = "#,##0;[Red]-#,##0";
    private static final String PERCENT_FORMAT = "0.0%;[Red]-0.0%";

    /** 表の外の文字 */
    final CellStyle plain;
    final CellStyle title;
    final CellStyle section;
    final CellStyle label;
    final CellStyle plainNumber;

    /** 表の中（罫線あり） */
    final CellStyle header;
    final CellStyle text;
    final CellStyle rowLabel;
    /** 値がない（前月比の初月など）ことを示す「-」 */
    final CellStyle dash;
    final CellStyle number;
    final CellStyle percent;
    final CellStyle date;
    final CellStyle totalText;
    final CellStyle totalNumber;
    final CellStyle totalPercent;
    final CellStyle rankA;
    final CellStyle rankB;
    final CellStyle rankC;

    private final XSSFWorkbook wb;
    private final DataFormat format;

    Styles(XSSFWorkbook wb) {
        this.wb = wb;
        this.format = wb.createDataFormat();
        Font normal = font(false, 11);
        Font bold = font(true, 11);

        plain = style(normal, null, false);
        title = style(font(true, 16), null, false);
        section = style(font(true, 12), null, false);
        label = style(bold, null, false);
        plainNumber = style(normal, NUMBER_FORMAT, false);

        header = style(bold, null, true);
        header.setAlignment(HorizontalAlignment.CENTER);
        fill(header, IndexedColors.GREY_25_PERCENT);

        text = style(normal, null, true);
        rowLabel = style(bold, null, true);
        dash = style(normal, null, true);
        dash.setAlignment(HorizontalAlignment.RIGHT);
        number = style(normal, NUMBER_FORMAT, true);
        percent = style(normal, PERCENT_FORMAT, true);
        date = style(normal, "yyyy/mm/dd", true);

        totalText = doubleTop(style(bold, null, true));
        totalNumber = doubleTop(style(bold, NUMBER_FORMAT, true));
        totalPercent = doubleTop(style(bold, PERCENT_FORMAT, true));

        rankA = centered(fill(style(bold, null, true), IndexedColors.LIGHT_GREEN));
        rankB = centered(fill(style(bold, null, true), IndexedColors.LIGHT_YELLOW));
        rankC = centered(fill(style(bold, null, true), IndexedColors.GREY_25_PERCENT));
    }

    private Font font(boolean bold, int size) {
        Font f = wb.createFont();
        f.setFontName(FONT_NAME);
        f.setBold(bold);
        f.setFontHeightInPoints((short) size);
        return f;
    }

    private CellStyle style(Font font, String dataFormat, boolean bordered) {
        CellStyle s = wb.createCellStyle();
        s.setFont(font);
        if (dataFormat != null) {
            s.setDataFormat(format.getFormat(dataFormat));
        }
        if (bordered) {
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
        }
        return s;
    }

    private static CellStyle fill(CellStyle s, IndexedColors color) {
        s.setFillForegroundColor(color.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private static CellStyle doubleTop(CellStyle s) {
        s.setBorderTop(BorderStyle.DOUBLE);
        return s;
    }

    private static CellStyle centered(CellStyle s) {
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }
}
