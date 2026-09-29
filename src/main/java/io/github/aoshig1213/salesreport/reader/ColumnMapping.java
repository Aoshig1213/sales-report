package io.github.aoshig1213.salesreport.reader;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ヘッダー行から「どの列が何番目にあるか」を決める。列の並び順は自由。
 */
public final class ColumnMapping {

    private final Map<Column, Integer> indexes;

    private ColumnMapping(Map<Column, Integer> indexes) {
        this.indexes = indexes;
    }

    /**
     * @throws InvalidRowException 必須の列が見つからないとき
     */
    public static ColumnMapping fromHeader(List<String> header) {
        Map<Column, Integer> indexes = new EnumMap<>(Column.class);
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i);
            if (h == null) {
                continue;
            }
            for (Column c : Column.values()) {
                if (c.matches(h) && !indexes.containsKey(c)) {
                    indexes.put(c, i);
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (Column c : Column.values()) {
            if (c.isRequired() && !indexes.containsKey(c)) {
                missing.add(c.label());
            }
        }
        if (!missing.isEmpty()) {
            throw new InvalidRowException("必須の列がありません: " + String.join("、", missing)
                    + "（1行目に見出しが必要です）");
        }
        return new ColumnMapping(indexes);
    }

    /** 行の値を列ごとに取り出す。列がない・値が空なら empty */
    public Optional<String> get(List<String> row, Column column) {
        Integer i = indexes.get(column);
        if (i == null || i >= row.size() || row.get(i) == null) {
            return Optional.empty();
        }
        String value = row.get(i).strip();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }
}
