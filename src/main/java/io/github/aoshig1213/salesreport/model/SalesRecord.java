package io.github.aoshig1213.salesreport.model;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 売上明細1行分。
 *
 * @param quantity   数量。マイナスは返品
 * @param amount     金額（円）。単価 × 数量
 * @param sourceFile 読み込んだファイル名（エラーや明細シートで元の行を追えるように持つ）
 * @param sourceRow  ファイル内の行番号（ヘッダーを1行目とする）
 */
public record SalesRecord(
        LocalDate date,
        String store,
        String productCode,
        String productName,
        String category,
        long unitPrice,
        int quantity,
        long amount,
        String sourceFile,
        int sourceRow) {

    public YearMonth month() {
        return YearMonth.from(date);
    }
}
