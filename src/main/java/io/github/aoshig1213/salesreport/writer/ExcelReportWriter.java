package io.github.aoshig1213.salesreport.writer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.AxisCrossBetween;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import io.github.aoshig1213.salesreport.aggregate.Pivot;
import io.github.aoshig1213.salesreport.aggregate.SalesReport;
import io.github.aoshig1213.salesreport.aggregate.SalesReport.ProductSummary;
import io.github.aoshig1213.salesreport.aggregate.SalesReport.StoreSummary;
import io.github.aoshig1213.salesreport.model.ReadError;
import io.github.aoshig1213.salesreport.model.SalesRecord;

/**
 * 集計結果を Excel 帳票（.xlsx）に書き出す。
 */
public class ExcelReportWriter {

    public static final String SHEET_SUMMARY = "サマリー";
    public static final String SHEET_STORES = "店舗別";
    public static final String SHEET_PRODUCTS = "商品別";
    public static final String SHEET_CATEGORIES = "カテゴリ別";
    public static final String SHEET_DETAILS = "明細";
    public static final String SHEET_ERRORS = "エラー";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy年M月");

    public void write(SalesReport report, List<SalesRecord> records, List<ReadError> errors,
            LocalDateTime createdAt, Path output) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            writeSummary(wb.createSheet(SHEET_SUMMARY), styles, report, errors.size(), createdAt);
            writeStores(wb.createSheet(SHEET_STORES), styles, report);
            writeProducts(wb.createSheet(SHEET_PRODUCTS), styles, report);
            writeCategories(wb.createSheet(SHEET_CATEGORIES), styles, report);
            writeDetails(wb.createSheet(SHEET_DETAILS), styles, records);
            writeErrors(wb.createSheet(SHEET_ERRORS), styles, errors);

            Files.createDirectories(output.toAbsolutePath().getParent());
            try (OutputStream out = Files.newOutputStream(output)) {
                wb.write(out);
            }
        }
    }

    // ---- サマリー ----

    private void writeSummary(XSSFSheet sheet, Styles s, SalesReport report, int errorCount,
            LocalDateTime createdAt) {
        set(sheet, 0, 0, "売上集計レポート", s.title);

        String[][] info = {
                { "集計期間", report.from().format(DATE) + " 〜 " + report.to().format(DATE) },
                { "作成日時", createdAt.format(DATE_TIME) },
        };
        int r = 2;
        for (String[] kv : info) {
            set(sheet, r, 0, kv[0], s.label);
            set(sheet, r++, 1, kv[1], s.plain);
        }
        set(sheet, r, 0, "売上合計", s.label);
        set(sheet, r++, 1, report.totalAmount(), s.plainNumber);
        set(sheet, r, 0, "販売数量", s.label);
        set(sheet, r++, 1, report.totalQuantity(), s.plainNumber);
        set(sheet, r, 0, "明細件数", s.label);
        set(sheet, r++, 1, report.recordCount(), s.plainNumber);
        set(sheet, r, 0, "読み込みエラー", s.label);
        set(sheet, r++, 1, errorCount == 0 ? "なし" : errorCount + " 件（「エラー」シートを確認してください）", s.plain);

        r++;
        set(sheet, r++, 0, "店舗別・月別の売上", s.section);
        int headerRow = r;
        int lastDataRow = writePivot(sheet, s, report.storeByMonth(), "店舗", headerRow);

        // 合計行の下に前月比
        int momRow = lastDataRow + 2;
        set(sheet, momRow, 0, "前月比", s.rowLabel);
        List<YearMonth> months = report.months();
        for (int i = 0; i < months.size(); i++) {
            if (i == 0) {
                set(sheet, momRow, 1 + i, "-", s.dash);
                continue;
            }
            long prev = report.storeByMonth().columnTotal(months.get(i - 1));
            long curr = report.storeByMonth().columnTotal(months.get(i));
            if (prev == 0) {
                set(sheet, momRow, 1 + i, "-", s.dash);
            } else {
                set(sheet, momRow, 1 + i, (double) curr / prev - 1, s.percent);
            }
        }
        set(sheet, momRow, 1 + months.size(), "", s.text);

        drawChart(sheet, report.storeByMonth(), headerRow, momRow + 2);

        // 期間などの長い値は右の空きセルにはみ出して表示されるので、B列以降は表に合わせる
        sheet.setColumnWidth(0, 18 * 256);
        for (int i = 0; i <= months.size(); i++) {
            sheet.setColumnWidth(1 + i, 14 * 256);
        }
    }

    /**
     * 行 × 月 の表と合計行を書く。
     *
     * @return 最後の明細行（合計行の1つ上）の行番号
     */
    private int writePivot(Sheet sheet, Styles s, Pivot<String, YearMonth> pivot, String rowLabel, int headerRow) {
        List<YearMonth> months = pivot.columns();
        set(sheet, headerRow, 0, rowLabel, s.header);
        for (int i = 0; i < months.size(); i++) {
            set(sheet, headerRow, 1 + i, months.get(i).format(MONTH), s.header);
        }
        set(sheet, headerRow, 1 + months.size(), "合計", s.header);

        int r = headerRow + 1;
        for (String key : pivot.rows()) {
            set(sheet, r, 0, key, s.text);
            for (int i = 0; i < months.size(); i++) {
                set(sheet, r, 1 + i, pivot.get(key, months.get(i)), s.number);
            }
            set(sheet, r, 1 + months.size(), pivot.rowTotal(key), s.number);
            r++;
        }

        set(sheet, r, 0, "合計", s.totalText);
        for (int i = 0; i < months.size(); i++) {
            set(sheet, r, 1 + i, pivot.columnTotal(months.get(i)), s.totalNumber);
        }
        set(sheet, r, 1 + months.size(), pivot.grandTotal(), s.totalNumber);
        return r - 1;
    }

    /**
     * 月別売上の棒グラフ（店舗ごとに色分け）。表のセルを参照するので、Excel で数字を直すとグラフも変わる。
     */
    private void drawChart(XSSFSheet sheet, Pivot<String, YearMonth> pivot, int headerRow, int topRow) {
        int monthCount = pivot.columns().size();
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, topRow, Math.max(6, monthCount + 2), topRow + 20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("月別売上（店舗別）");
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);

        XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
        yAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        yAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        yAxis.setNumberFormat("#,##0");

        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(headerRow, headerRow, 1, monthCount));
        XDDFBarChartData data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, xAxis, yAxis);
        data.setBarDirection(BarDirection.COL);
        data.setVaryColors(false);

        for (int i = 0; i < pivot.rows().size(); i++) {
            int row = headerRow + 1 + i;
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                    sheet, new CellRangeAddress(row, row, 1, monthCount));
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(pivot.rows().get(i), new CellReference(sheet.getSheetName(), row, 0, true, true));
        }
        chart.plot(data);
    }

    // ---- 店舗別 ----

    private void writeStores(Sheet sheet, Styles s, SalesReport report) {
        header(sheet, s, "順位", "店舗", "売上", "構成比", "販売数量", "明細件数");
        int r = 1;
        for (StoreSummary st : report.stores()) {
            set(sheet, r, 0, r, s.number);
            set(sheet, r, 1, st.store(), s.text);
            set(sheet, r, 2, st.amount(), s.number);
            set(sheet, r, 3, st.share(), s.percent);
            set(sheet, r, 4, st.quantity(), s.number);
            set(sheet, r, 5, st.recordCount(), s.number);
            r++;
        }
        set(sheet, r, 0, "", s.totalText);
        set(sheet, r, 1, "合計", s.totalText);
        set(sheet, r, 2, report.totalAmount(), s.totalNumber);
        set(sheet, r, 3, 1.0, s.totalPercent);
        set(sheet, r, 4, report.totalQuantity(), s.totalNumber);
        set(sheet, r, 5, report.recordCount(), s.totalNumber);
        sheet.createFreezePane(0, 1);
        widths(sheet, 8, 18, 14, 10, 12, 12);
    }

    // ---- 商品別 ----

    private void writeProducts(Sheet sheet, Styles s, SalesReport report) {
        header(sheet, s, "順位", "商品コード", "商品名", "カテゴリ", "販売数量", "売上", "構成比", "累計構成比", "ABC区分");
        int r = 1;
        for (ProductSummary p : report.products()) {
            set(sheet, r, 0, p.rank(), s.number);
            set(sheet, r, 1, p.productCode(), s.text);
            set(sheet, r, 2, p.productName(), s.text);
            set(sheet, r, 3, p.category(), s.text);
            set(sheet, r, 4, p.quantity(), s.number);
            set(sheet, r, 5, p.amount(), s.number);
            set(sheet, r, 6, p.share(), s.percent);
            set(sheet, r, 7, p.cumulativeShare(), s.percent);
            set(sheet, r, 8, p.abcRank().name(), switch (p.abcRank()) {
                case A -> s.rankA;
                case B -> s.rankB;
                case C -> s.rankC;
            });
            r++;
        }
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, 8));
        sheet.createFreezePane(0, 1);
        // オートフィルターのボタンが見出しに重ならないよう、少し広めに取る
        widths(sheet, 9, 15, 28, 13, 13, 14, 11, 14, 12);

        r += 1;
        set(sheet, r++, 0, "ABC区分: 売上の大きい順に並べて累計構成比で分けたもの。A = 売上の約7割を占める主力商品、B = 次の約2割、C = 残り", s.plain);
    }

    // ---- カテゴリ別 ----

    private void writeCategories(Sheet sheet, Styles s, SalesReport report) {
        writePivot(sheet, s, report.categoryByMonth(), "カテゴリ", 0);
        sheet.createFreezePane(1, 1);
        sheet.setColumnWidth(0, 16 * 256);
        for (int i = 0; i <= report.months().size(); i++) {
            sheet.setColumnWidth(1 + i, 14 * 256);
        }
    }

    // ---- 明細 ----

    private void writeDetails(Sheet sheet, Styles s, List<SalesRecord> records) {
        header(sheet, s, "日付", "店舗", "商品コード", "商品名", "カテゴリ", "単価", "数量", "金額", "元ファイル", "行");
        List<SalesRecord> sorted = records.stream()
                .sorted(Comparator.comparing(SalesRecord::date)
                        .thenComparing(SalesRecord::store)
                        .thenComparing(SalesRecord::sourceFile)
                        .thenComparingInt(SalesRecord::sourceRow))
                .toList();
        int r = 1;
        for (SalesRecord rec : sorted) {
            Row row = sheet.createRow(r);
            Cell dateCell = row.createCell(0);
            dateCell.setCellValue(rec.date());
            dateCell.setCellStyle(s.date);
            set(sheet, r, 1, rec.store(), s.text);
            set(sheet, r, 2, rec.productCode(), s.text);
            set(sheet, r, 3, rec.productName(), s.text);
            set(sheet, r, 4, rec.category(), s.text);
            set(sheet, r, 5, rec.unitPrice(), s.number);
            set(sheet, r, 6, rec.quantity(), s.number);
            set(sheet, r, 7, rec.amount(), s.number);
            set(sheet, r, 8, rec.sourceFile(), s.text);
            set(sheet, r, 9, rec.sourceRow(), s.number);
            r++;
        }
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, 9));
        sheet.createFreezePane(0, 1);
        widths(sheet, 13, 14, 15, 28, 13, 10, 9, 12, 22, 7);
    }

    // ---- エラー ----

    private void writeErrors(Sheet sheet, Styles s, List<ReadError> errors) {
        if (errors.isEmpty()) {
            set(sheet, 0, 0, "読み込めなかった行はありません。", s.plain);
            widths(sheet, 40);
            return;
        }
        header(sheet, s, "ファイル", "行", "内容");
        int r = 1;
        for (ReadError e : errors) {
            set(sheet, r, 0, e.file(), s.text);
            if (e.row() == 0) {
                set(sheet, r, 1, "-", s.dash);
            } else {
                set(sheet, r, 1, e.row(), s.number);
            }
            set(sheet, r, 2, e.message(), s.text);
            r++;
        }
        sheet.createFreezePane(0, 1);
        widths(sheet, 24, 6, 90);
    }

    // ---- helpers ----

    private static void header(Sheet sheet, Styles s, String... labels) {
        for (int i = 0; i < labels.length; i++) {
            set(sheet, 0, i, labels[i], s.header);
        }
    }

    /** 列幅を文字数で指定する（日本語が入る列は多めに取る） */
    private static void widths(Sheet sheet, int... chars) {
        for (int i = 0; i < chars.length; i++) {
            sheet.setColumnWidth(i, chars[i] * 256);
        }
    }

    private static Cell cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row);
        if (r == null) {
            r = sheet.createRow(row);
        }
        return r.createCell(col);
    }

    private static void set(Sheet sheet, int row, int col, String value, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void set(Sheet sheet, int row, int col, double value, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }
}
