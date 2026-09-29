package io.github.aoshig1213.salesreport.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.aoshig1213.salesreport.model.ReadResult;
import io.github.aoshig1213.salesreport.model.SalesRecord;

class SalesFileLoaderTest {

    @TempDir
    Path dir;

    private final SalesFileLoader loader = new SalesFileLoader();

    @Test
    void ShiftJISのCSVを読める() throws IOException {
        Files.write(dir.resolve("umeda.csv"), """
                売上日,店舗名,品番,品名,分類,数量,販売単価
                2026/04/01,梅田店,D-001,緑茶 500ml,飲料,2,"1,150"
                """.getBytes(CharsetDetector.WINDOWS_31J));

        ReadResult result = load();

        assertThat(result.errors()).isEmpty();
        SalesRecord r = result.records().getFirst();
        assertThat(r.store()).isEqualTo("梅田店");
        assertThat(r.productName()).isEqualTo("緑茶 500ml");
        assertThat(r.amount()).isEqualTo(2300);
    }

    @Test
    void 不正な行はエラーにして他の行は読む() throws IOException {
        Files.writeString(dir.resolve("shibuya.csv"), """
                日付,店舗,商品コード,商品名,単価,数量
                2026/04/01,渋谷店,D-001,緑茶,150,1

                2026/04/31,渋谷店,D-001,緑茶,150,1
                2026/04/02,渋谷店,D-001,緑茶,150,1
                """, StandardCharsets.UTF_8);

        ReadResult result = load();

        assertThat(result.records()).hasSize(2);
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.file()).isEqualTo("shibuya.csv");
            assertThat(e.row()).isEqualTo(4); // 空行も数えたファイル上の行番号
        });
    }

    @Test
    void Excelの日付セルと数値セルと数式を読める() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy/mm/dd"));
            Row h = sheet.createRow(0);
            String[] header = { "日付", "店舗", "商品コード", "商品名", "単価", "数量", "金額" };
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            Row r = sheet.createRow(1);
            r.createCell(0).setCellValue(LocalDate.of(2026, 4, 1));
            r.getCell(0).setCellStyle(dateStyle);
            r.createCell(1).setCellValue("天神店");
            r.createCell(2).setCellValue("D-001");
            r.createCell(3).setCellValue("緑茶");
            r.createCell(4).setCellValue(150);
            r.createCell(5).setCellValue(3);
            r.createCell(6).setCellFormula("E2*F2");
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (OutputStream out = Files.newOutputStream(dir.resolve("tenjin.xlsx"))) {
                wb.write(out);
            }
        }

        ReadResult result = load();

        assertThat(result.errors()).isEmpty();
        SalesRecord r = result.records().getFirst();
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.quantity()).isEqualTo(3);
        assertThat(r.amount()).isEqualTo(450);
        assertThat(r.sourceRow()).isEqualTo(2);
    }

    @Test
    void 必須列がないファイルはファイル単位のエラーにする() throws IOException {
        Files.writeString(dir.resolve("bad.csv"), "日付,店舗\n2026/04/01,渋谷店\n");

        ReadResult result = load();

        assertThat(result.records()).isEmpty();
        assertThat(result.errors()).singleElement().satisfies(e -> {
            assertThat(e.row()).isZero();
            assertThat(e.message()).contains("必須の列がありません");
        });
    }

    @Test
    void 壊れたExcelがあっても他のファイルは読む() throws IOException {
        Files.writeString(dir.resolve("a.csv"), "日付,店舗,商品コード,商品名,単価,数量\n2026/04/01,渋谷店,D-001,緑茶,150,1\n");
        Files.writeString(dir.resolve("b.xlsx"), "this is not an excel file");

        ReadResult result = load();

        assertThat(result.records()).hasSize(1);
        assertThat(result.errors()).singleElement()
                .satisfies(e -> assertThat(e.message()).startsWith("ファイルを開けません"));
    }

    @Test
    void 対象外のファイルとExcelの一時ファイルは読まない() throws IOException {
        Files.writeString(dir.resolve("memo.txt"), "memo");
        Files.writeString(dir.resolve("~$tenjin.xlsx"), "lock");
        Files.writeString(dir.resolve("b.csv"), "");
        Files.writeString(dir.resolve("a.CSV"), "");

        assertThat(loader.listFiles(dir)).extracting(p -> p.getFileName().toString())
                .containsExactly("a.CSV", "b.csv");
    }

    private ReadResult load() throws IOException {
        List<Path> files = loader.listFiles(dir);
        return loader.load(files, fr -> {
        });
    }
}
