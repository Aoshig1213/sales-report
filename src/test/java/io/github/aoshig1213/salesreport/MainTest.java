package io.github.aoshig1213.salesreport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.aoshig1213.salesreport.writer.ExcelReportWriter;

/** サンプルデータを使って、入力から帳票出力まで通して確認する */
class MainTest {

    @TempDir
    Path tmp;

    Path input;
    Path output;
    ByteArrayOutputStream console = new ByteArrayOutputStream();

    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 1, 9, 30).atZone(ZoneId.of("Asia/Tokyo")).toInstant(), ZoneId.of("Asia/Tokyo"));

    @BeforeEach
    void setUp() throws IOException {
        input = tmp.resolve("input");
        output = tmp.resolve("output");
        Files.createDirectories(input);
        new SampleDataGenerator().generate(input);
    }

    private int run(String... args) {
        return new Main(new PrintStream(console, true, StandardCharsets.UTF_8), clock).run(args);
    }

    private String console() {
        return console.toString(StandardCharsets.UTF_8);
    }

    @Test
    void サンプルデータから帳票を作る() throws IOException {
        int exit = run(input.toString(), output.toString());

        assertThat(exit).isEqualTo(Main.OK);
        Path report = output.resolve("売上集計_20260701_093000.xlsx");
        assertThat(report).exists();
        assertThat(console()).contains("集計しました").contains("読み込めなかった行が 3 件");

        try (InputStream in = Files.newInputStream(report); XSSFWorkbook wb = new XSSFWorkbook(in)) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(6);
            assertThat(wb.getSheetName(0)).isEqualTo(ExcelReportWriter.SHEET_SUMMARY);

            // サマリーのグラフ（店舗3つ分の系列）
            XSSFSheet summary = wb.getSheet(ExcelReportWriter.SHEET_SUMMARY);
            assertThat(summary.getDrawingPatriarch().getCharts()).singleElement()
                    .satisfies(chart -> assertThat(chart.getChartSeries().getFirst().getSeriesCount()).isEqualTo(3));

            // エラーシートに意図的に入れた3件（存在しない日付・金額の不一致・数字でない数量）
            Sheet errors = wb.getSheet(ExcelReportWriter.SHEET_ERRORS);
            DataFormatter fmt = new DataFormatter();
            assertThat(errors.getLastRowNum()).isEqualTo(3);
            StringBuilder messages = new StringBuilder();
            for (int r = 1; r <= 3; r++) {
                messages.append(fmt.formatCellValue(errors.getRow(r).getCell(2)));
            }
            assertThat(messages.toString()).contains("2026/04/31").contains("一致しません").contains("「三」");

            // 明細シートの件数 = 店舗別シートの明細件数の合計
            Sheet details = wb.getSheet(ExcelReportWriter.SHEET_DETAILS);
            Sheet stores = wb.getSheet(ExcelReportWriter.SHEET_STORES);
            int totalRow = stores.getLastRowNum();
            assertThat((int) stores.getRow(totalRow).getCell(5).getNumericCellValue())
                    .isEqualTo(details.getLastRowNum());
        }
    }

    @Test
    void 入力フォルダがなければ終了コード1() {
        assertThat(run(tmp.resolve("nothing").toString(), output.toString())).isEqualTo(Main.INPUT_ERROR);
        assertThat(console()).contains("入力フォルダが見つかりません");
    }

    @Test
    void 読めるファイルがなければ終了コード1() throws IOException {
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        Files.writeString(empty.resolve("memo.txt"), "memo");

        assertThat(run(empty.toString(), output.toString())).isEqualTo(Main.INPUT_ERROR);
        assertThat(console()).contains("CSV / Excel ファイルがありません");
    }

    @Test
    void 全行エラーなら帳票を作らず理由を表示する() throws IOException {
        Path bad = Files.createDirectories(tmp.resolve("bad"));
        Files.writeString(bad.resolve("a.csv"), "日付,店舗\n2026/04/01,渋谷店\n");

        assertThat(run(bad.toString(), output.toString())).isEqualTo(Main.INPUT_ERROR);
        assertThat(console()).contains("集計できる明細がありませんでした").contains("必須の列がありません");
        assertThat(output).doesNotExist();
    }
}
