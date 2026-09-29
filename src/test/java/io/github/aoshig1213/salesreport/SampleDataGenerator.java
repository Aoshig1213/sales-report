package io.github.aoshig1213.salesreport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import io.github.aoshig1213.salesreport.reader.CharsetDetector;

/**
 * samples/input のサンプルデータを作る（リポジトリに入っているものはこれで作成済み）。
 * 乱数の種を固定しているので、何度実行しても同じ内容になる。
 *
 * <pre>
 * ./mvnw test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.aoshig1213.salesreport.SampleDataGenerator -Dexec.args=samples/input
 * </pre>
 */
public final class SampleDataGenerator {

    record Product(String code, String name, String category, int price, int popularity) {
    }

    record Sale(LocalDate date, Product product, int quantity) {
    }

    private static final List<Product> PRODUCTS = List.of(
            new Product("D-001", "緑茶 500ml", "飲料", 150, 30),
            new Product("D-002", "ミネラルウォーター 2L", "飲料", 120, 22),
            new Product("D-003", "ドリップコーヒー 10袋", "飲料", 680, 8),
            new Product("S-001", "ポテトチップス うすしお", "菓子", 160, 18),
            new Product("S-002", "チョコレート アソート", "菓子", 450, 9),
            new Product("S-003", "せんべい 詰め合わせ", "菓子", 880, 4),
            new Product("F-001", "カップ麺 しょうゆ", "食品", 210, 16),
            new Product("F-002", "レトルトカレー 中辛", "食品", 320, 10),
            new Product("F-003", "パックご飯 3個入", "食品", 420, 7),
            new Product("H-001", "ティッシュ 5箱", "日用品", 398, 6),
            new Product("H-002", "食器用洗剤", "日用品", 248, 5),
            new Product("H-003", "乾電池 単3 8本", "日用品", 980, 2));

    private static final DateTimeFormatter SLASH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Random random = new Random(20260401L);

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "samples/input");
        Files.createDirectories(dir);
        new SampleDataGenerator().generate(dir);
        System.out.println("Generated sample files in " + dir.toAbsolutePath());
    }

    void generate(Path dir) throws IOException {
        for (YearMonth month = YearMonth.of(2026, 4); !month.isAfter(YearMonth.of(2026, 6)); month = month.plusMonths(1)) {
            writeShibuya(dir.resolve("渋谷店_" + month + ".csv"), sales(month, 1.3), month);
            writeUmeda(dir.resolve("梅田店_" + month + ".csv"), sales(month, 1.0), month);
            writeTenjin(dir.resolve("天神店_" + month + ".xlsx"), sales(month, 0.7));
        }
    }

    /** 1日あたりの取引数を店舗の規模（scale）と曜日で変える */
    private List<Sale> sales(YearMonth month, double scale) {
        List<Sale> sales = new ArrayList<>();
        int totalPopularity = PRODUCTS.stream().mapToInt(Product::popularity).sum();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            boolean weekend = date.getDayOfWeek().getValue() >= 6;
            int count = (int) Math.round((weekend ? 9 : 6) * scale) + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                int pick = random.nextInt(totalPopularity);
                Product product = null;
                for (Product p : PRODUCTS) {
                    pick -= p.popularity();
                    if (pick < 0) {
                        product = p;
                        break;
                    }
                }
                sales.add(new Sale(date, product, 1 + random.nextInt(product.price() < 300 ? 6 : 3)));
            }
        }
        return sales;
    }

    /** 渋谷店: UTF-8（BOM 付き）の CSV。金額列あり */
    private void writeShibuya(Path file, List<Sale> sales, YearMonth month) throws IOException {
        StringBuilder sb = new StringBuilder("日付,店舗,商品コード,商品名,カテゴリ,単価,数量,金額\r\n");
        for (int i = 0; i < sales.size(); i++) {
            Sale s = sales.get(i);
            long amount = (long) s.product().price() * s.quantity();
            if (month.getMonthValue() == 5 && i == 40) {
                amount += 100; // 金額の入力ミス（単価 × 数量と合わない）
            }
            sb.append(String.join(",", s.date().format(SLASH), "渋谷店", s.product().code(),
                    quote(s.product().name()), s.product().category(), String.valueOf(s.product().price()),
                    String.valueOf(s.quantity()), String.valueOf(amount))).append("\r\n");
        }
        // 月末に返品が1件
        Product returned = PRODUCTS.get(4);
        sb.append(String.join(",", month.atEndOfMonth().format(SLASH), "渋谷店", returned.code(),
                returned.name(), returned.category(), String.valueOf(returned.price()), "-1",
                String.valueOf(-returned.price()))).append("\r\n");
        write(file, sb.toString(), StandardCharsets.UTF_8, true);
    }

    /** 梅田店: Shift_JIS（Excel の「CSV（コンマ区切り）」保存）。見出しの名前と並び順が違い、金額列がない */
    private void writeUmeda(Path file, List<Sale> sales, YearMonth month) throws IOException {
        StringBuilder sb = new StringBuilder("売上日,店舗名,品番,品名,分類,数量,販売単価\r\n");
        for (int i = 0; i < sales.size(); i++) {
            Sale s = sales.get(i);
            String date = s.date().format(SLASH);
            String quantity = String.valueOf(s.quantity());
            if (month.getMonthValue() == 4 && i == 10) {
                date = "2026/04/31"; // 存在しない日付
            }
            if (month.getMonthValue() == 6 && i == 25) {
                quantity = "三"; // 数字でない
            }
            sb.append(String.join(",", date, "梅田店", s.product().code(), quote(s.product().name()),
                    s.product().category(), quantity, "\"" + String.format("%,d", s.product().price()) + "\""))
                    .append("\r\n");
        }
        write(file, sb.toString(), CharsetDetector.WINDOWS_31J, false);
    }

    /** 天神店: Excel。日付は日付セル、数値は数値セル */
    private void writeTenjin(Path file, List<Sale> sales) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("売上");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy/mm/dd"));

            String[] header = { "日付", "店舗", "商品コード", "商品名", "カテゴリ", "単価", "数量", "金額" };
            Row h = sheet.createRow(0);
            for (int i = 0; i < header.length; i++) {
                h.createCell(i).setCellValue(header[i]);
            }
            int r = 1;
            for (Sale s : sales) {
                Row row = sheet.createRow(r);
                row.createCell(0).setCellValue(s.date());
                row.getCell(0).setCellStyle(dateStyle);
                row.createCell(1).setCellValue("天神店");
                row.createCell(2).setCellValue(s.product().code());
                row.createCell(3).setCellValue(s.product().name());
                row.createCell(4).setCellValue(s.product().category());
                row.createCell(5).setCellValue(s.product().price());
                row.createCell(6).setCellValue(s.quantity());
                row.createCell(7).setCellFormula("F" + (r + 1) + "*G" + (r + 1));
                r++;
            }
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
    }

    private static String quote(String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
    }

    private static void write(Path file, String content, Charset charset, boolean bom) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            if (bom) {
                out.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });
            }
            out.write(content.getBytes(charset));
        }
    }
}
