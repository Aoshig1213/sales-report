package io.github.aoshig1213.salesreport;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.github.aoshig1213.salesreport.aggregate.SalesAggregator;
import io.github.aoshig1213.salesreport.aggregate.SalesReport;
import io.github.aoshig1213.salesreport.model.ReadResult;
import io.github.aoshig1213.salesreport.reader.SalesFileLoader;
import io.github.aoshig1213.salesreport.writer.ExcelReportWriter;

/**
 * 売上集計ツール。
 *
 * <pre>
 * java -jar sales-report.jar [入力フォルダ] [出力フォルダ]
 *   入力フォルダ: 売上の CSV / Excel を置くフォルダ（省略時 input）
 *   出力フォルダ: 帳票を書き出すフォルダ（省略時 output）
 * </pre>
 */
public final class Main {

    public static final int OK = 0;
    /** 入力フォルダがない、集計できる明細がない等、利用者が直せるエラー */
    public static final int INPUT_ERROR = 1;
    public static final int UNEXPECTED_ERROR = 2;

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final PrintStream out;
    private final Clock clock;

    Main(PrintStream out, Clock clock) {
        this.out = out;
        this.clock = clock;
    }

    public static void main(String[] args) {
        System.exit(new Main(System.out, Clock.systemDefaultZone()).run(args));
    }

    int run(String[] args) {
        if (args.length > 0 && (args[0].equals("-h") || args[0].equals("--help"))) {
            out.println("使い方: java -jar sales-report.jar [入力フォルダ] [出力フォルダ]");
            out.println("  入力フォルダの CSV / Excel（.csv .xlsx .xls）をすべて読み込み、集計した Excel を出力します。");
            out.println("  省略時は input フォルダから読み込み、output フォルダに出力します。");
            return OK;
        }
        Path inputDir = Path.of(args.length > 0 ? args[0] : "input");
        Path outputDir = Path.of(args.length > 1 ? args[1] : "output");

        try {
            return execute(inputDir, outputDir);
        } catch (Exception e) {
            out.println();
            out.println("[エラー] 予期しないエラーで処理を中断しました: " + e);
            return UNEXPECTED_ERROR;
        }
    }

    private int execute(Path inputDir, Path outputDir) throws Exception {
        if (!Files.isDirectory(inputDir)) {
            out.println("[エラー] 入力フォルダが見つかりません: " + inputDir.toAbsolutePath());
            return INPUT_ERROR;
        }

        SalesFileLoader loader = new SalesFileLoader();
        List<Path> files = loader.listFiles(inputDir);
        if (files.isEmpty()) {
            out.println("[エラー] 入力フォルダに CSV / Excel ファイルがありません: " + inputDir.toAbsolutePath());
            return INPUT_ERROR;
        }

        out.println("読み込み中（" + files.size() + " ファイル）");
        ReadResult result = loader.load(files, fr -> out.printf("  %-30s %,6d 件%s%n",
                fr.file().getFileName(), fr.result().records().size(),
                fr.result().errors().isEmpty() ? "" : "（エラー " + fr.result().errors().size() + " 件）"));

        if (result.records().isEmpty()) {
            out.println("[エラー] 集計できる明細がありませんでした。ファイルの内容を確認してください。");
            result.errors().stream().limit(10).forEach(e ->
                    out.println("  " + e.file() + (e.row() > 0 ? " " + e.row() + "行目" : "") + ": " + e.message()));
            return INPUT_ERROR;
        }

        SalesReport report = new SalesAggregator().aggregate(result.records());
        LocalDateTime now = LocalDateTime.now(clock);
        Path output = outputDir.resolve("売上集計_" + now.format(FILE_TIMESTAMP) + ".xlsx");
        new ExcelReportWriter().write(report, result.records(), result.errors(), now, output);

        out.println();
        out.printf("集計しました: %,d 件、売上合計 %,d 円%n", report.recordCount(), report.totalAmount());
        if (!result.errors().isEmpty()) {
            out.printf("読み込めなかった行が %,d 件あります。帳票の「エラー」シートを確認してください。%n",
                    result.errors().size());
        }
        out.println("出力先: " + output.toAbsolutePath());
        return OK;
    }
}
