package io.github.aoshig1213.salesreport.reader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

import io.github.aoshig1213.salesreport.model.SalesRecord;

/**
 * 1行分の文字列を検証して {@link SalesRecord} にする。CSV と Excel で共通。
 * 問題が複数あっても1つずつ直させないよう、まとめてメッセージにする。
 */
public final class RowParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("uuuu/M/d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu年M月d日").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT));

    private final ColumnMapping mapping;
    private final String fileName;

    public RowParser(ColumnMapping mapping, String fileName) {
        this.mapping = mapping;
        this.fileName = fileName;
    }

    /**
     * @param rowNumber ファイル内の行番号（エラー表示用）
     * @throws InvalidRowException 値が不正なとき
     */
    public SalesRecord parse(List<String> row, int rowNumber) {
        List<String> problems = new ArrayList<>();

        LocalDate date = parseDate(row, problems);
        String store = required(row, Column.STORE, problems);
        String productCode = required(row, Column.PRODUCT_CODE, problems);
        String productName = required(row, Column.PRODUCT_NAME, problems);
        String category = mapping.get(row, Column.CATEGORY).orElse("（未分類）");
        Long unitPrice = parseLong(row, Column.UNIT_PRICE, problems);
        Long quantity = parseLong(row, Column.QUANTITY, problems);

        if (unitPrice != null && unitPrice < 0) {
            problems.add("単価がマイナスです");
        }
        if (quantity != null && (quantity == 0 || Math.abs(quantity) > Integer.MAX_VALUE)) {
            problems.add("数量が0か、大きすぎます");
        }

        Long amount = null;
        if (unitPrice != null && quantity != null) {
            amount = unitPrice * quantity;
            Long written = mapping.get(row, Column.AMOUNT).isPresent()
                    ? parseLong(row, Column.AMOUNT, problems)
                    : null;
            if (written != null && written.longValue() != amount) {
                problems.add("金額（" + written + "）が 単価 × 数量（" + amount + "）と一致しません");
            }
        }

        if (!problems.isEmpty()) {
            throw new InvalidRowException(String.join(" / ", problems));
        }
        return new SalesRecord(date, store, productCode, productName, category,
                unitPrice, quantity.intValue(), amount, fileName, rowNumber);
    }

    private String required(List<String> row, Column column, List<String> problems) {
        return mapping.get(row, column).orElseGet(() -> {
            problems.add(column.label() + "が空です");
            return null;
        });
    }

    private LocalDate parseDate(List<String> row, List<String> problems) {
        String value = required(row, Column.DATE, problems);
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, f);
            } catch (DateTimeParseException ignored) {
                // 次の形式を試す
            }
        }
        problems.add("日付「" + value + "」を読み取れません（例: 2026/04/01）");
        return null;
    }

    /** 「1,200」「¥1,200」「1200円」なども数値として読む */
    private Long parseLong(List<String> row, Column column, List<String> problems) {
        String value = required(row, column, problems);
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[,，¥￥円\\s]", "")
                .replace('－', '-').replace('−', '-');
        cleaned = toHalfWidthDigits(cleaned);
        if (cleaned.endsWith(".0")) {
            // Excel の数値セルは「3.0」のように来ることがある
            cleaned = cleaned.substring(0, cleaned.length() - 2);
        }
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            problems.add(column.label() + "「" + value + "」が整数ではありません");
            return null;
        }
    }

    private static String toHalfWidthDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= '０' && c <= '９' ? (char) (c - '０' + '0') : c);
        }
        return sb.toString();
    }
}
