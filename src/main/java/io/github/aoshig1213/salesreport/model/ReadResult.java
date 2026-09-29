package io.github.aoshig1213.salesreport.model;

import java.util.ArrayList;
import java.util.List;

/** ファイルを読んだ結果。正常に読めた明細と、読めなかった行の両方を持つ */
public record ReadResult(List<SalesRecord> records, List<ReadError> errors) {

    public static ReadResult fileError(String file, String message) {
        return new ReadResult(List.of(), List.of(new ReadError(file, 0, message)));
    }

    public static ReadResult merge(List<ReadResult> results) {
        List<SalesRecord> records = new ArrayList<>();
        List<ReadError> errors = new ArrayList<>();
        for (ReadResult r : results) {
            records.addAll(r.records());
            errors.addAll(r.errors());
        }
        return new ReadResult(List.copyOf(records), List.copyOf(errors));
    }
}
