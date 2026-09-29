package io.github.aoshig1213.salesreport.model;

/**
 * 読み込めなかった行（またはファイル）。
 *
 * @param row 行番号。ファイル全体のエラー（必須列がない等）は 0
 */
public record ReadError(String file, int row, String message) {
}
