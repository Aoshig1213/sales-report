package io.github.aoshig1213.salesreport.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.github.aoshig1213.salesreport.model.ReadResult;

/**
 * フォルダ内の CSV / Excel をすべて読む。
 */
public class SalesFileLoader {

    private final CsvSalesReader csvReader = new CsvSalesReader();
    private final ExcelSalesReader excelReader = new ExcelSalesReader();

    /** 読み込み対象のファイル（ファイル名順） */
    public List<Path> listFiles(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> readerFor(p).isPresent())
                    .sorted()
                    .toList();
        }
    }

    /**
     * @param onFileRead 1ファイル読むごとに呼ぶ（進み具合の表示用）
     */
    public ReadResult load(List<Path> files, Consumer<FileResult> onFileRead) {
        List<ReadResult> results = new ArrayList<>();
        for (Path file : files) {
            ReadResult result;
            try {
                result = readerFor(file).orElseThrow().read(file);
            } catch (IOException | RuntimeException e) {
                // 壊れたファイルやパスワード付きファイルがあっても、他のファイルの集計は続ける
                result = ReadResult.fileError(file.getFileName().toString(), "ファイルを開けません: " + e.getMessage());
            }
            results.add(result);
            onFileRead.accept(new FileResult(file, result));
        }
        return ReadResult.merge(results);
    }

    private Optional<SalesFileReader> readerFor(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("~$")) {
            // Excel で開いている間にできる一時ファイル
            return Optional.empty();
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return Optional.of(csvReader);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return Optional.of(excelReader);
        }
        return Optional.empty();
    }

    public record FileResult(Path file, ReadResult result) {
    }
}
