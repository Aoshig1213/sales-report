package io.github.aoshig1213.salesreport.reader;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import io.github.aoshig1213.salesreport.model.ReadResult;

public class CsvSalesReader implements SalesFileReader {

    @Override
    public ReadResult read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset charset = CharsetDetector.detect(bytes);
        int offset = CharsetDetector.hasUtf8Bom(bytes) ? 3 : 0;
        String text = new String(bytes, offset, bytes.length - offset, charset);

        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();
        // 空行も1レコードとして数える（飛ばすとエラー表示の行番号がファイルの行とずれる）。空行自体は後で読み飛ばす
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(false).get()
                .parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                rows.add(record.toList());
                // 何件目のレコードか。値の中に改行がなければファイルの行番号と同じ
                rowNumbers.add((int) record.getRecordNumber());
            }
        } catch (IllegalStateException | UncheckedIOException e) {
            return ReadResult.fileError(file.getFileName().toString(), "CSV の形式が正しくありません: " + e.getMessage());
        }
        return RowsToRecords.convert(file.getFileName().toString(), rows, rowNumbers);
    }
}
