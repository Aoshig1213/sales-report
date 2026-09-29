package io.github.aoshig1213.salesreport.reader;

import java.util.ArrayList;
import java.util.List;

import io.github.aoshig1213.salesreport.model.ReadError;
import io.github.aoshig1213.salesreport.model.ReadResult;
import io.github.aoshig1213.salesreport.model.SalesRecord;

/**
 * 「1行目がヘッダー、2行目以降が明細」の行リストを明細に変換する。CSV と Excel で共通。
 */
final class RowsToRecords {

    private RowsToRecords() {
    }

    /**
     * @param rows 各行の値。rowNumbers と同じ順
     * @param rowNumbers ファイル上の行番号（空行を飛ばしても元の行番号を表示できるように）
     */
    static ReadResult convert(String fileName, List<List<String>> rows, List<Integer> rowNumbers) {
        if (rows.isEmpty()) {
            return ReadResult.fileError(fileName, "データがありません");
        }

        ColumnMapping mapping;
        try {
            mapping = ColumnMapping.fromHeader(rows.getFirst());
        } catch (InvalidRowException e) {
            return ReadResult.fileError(fileName, e.getMessage());
        }

        RowParser parser = new RowParser(mapping, fileName);
        List<SalesRecord> records = new ArrayList<>();
        List<ReadError> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            int rowNumber = rowNumbers.get(i);
            try {
                records.add(parser.parse(row, rowNumber));
            } catch (InvalidRowException e) {
                errors.add(new ReadError(fileName, rowNumber, e.getMessage()));
            }
        }
        return new ReadResult(records, errors);
    }

    private static boolean isBlank(List<String> row) {
        return row.stream().allMatch(v -> v == null || v.isBlank());
    }
}
