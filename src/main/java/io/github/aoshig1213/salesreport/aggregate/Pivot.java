package io.github.aoshig1213.salesreport.aggregate;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 行 × 列 のクロス集計表（Excel のピボットテーブルと同じもの）。
 *
 * @param <R> 行の見出し（店舗・カテゴリ等）
 * @param <C> 列の見出し（月等）
 */
public final class Pivot<R, C> {

    private final List<R> rows;
    private final List<C> columns;
    private final Map<R, Map<C, Long>> values;

    private Pivot(List<R> rows, List<C> columns, Map<R, Map<C, Long>> values) {
        this.rows = rows;
        this.columns = columns;
        this.values = values;
    }

    /**
     * @param columns 列の並び（データがない列も表示したいので外から渡す）
     * 行は合計の大きい順に並べる
     */
    public static <T, R, C> Pivot<R, C> of(List<T> items, Function<T, R> rowKey, Function<T, C> columnKey,
            ToLongFunction<T> value, List<C> columns) {
        Map<R, Map<C, Long>> values = new HashMap<>();
        Set<R> rowKeys = new LinkedHashSet<>();
        for (T item : items) {
            R r = rowKey.apply(item);
            rowKeys.add(r);
            values.computeIfAbsent(r, k -> new HashMap<>())
                    .merge(columnKey.apply(item), value.applyAsLong(item), Long::sum);
        }
        Pivot<R, C> unsorted = new Pivot<>(List.copyOf(rowKeys), List.copyOf(columns), values);
        List<R> sortedRows = rowKeys.stream()
                .sorted(Comparator.comparingLong(unsorted::rowTotal).reversed())
                .toList();
        return new Pivot<>(sortedRows, List.copyOf(columns), values);
    }

    public List<R> rows() {
        return rows;
    }

    public List<C> columns() {
        return columns;
    }

    public long get(R row, C column) {
        return values.getOrDefault(row, Map.of()).getOrDefault(column, 0L);
    }

    public long rowTotal(R row) {
        return values.getOrDefault(row, Map.of()).values().stream().mapToLong(Long::longValue).sum();
    }

    public long columnTotal(C column) {
        return rows.stream().mapToLong(r -> get(r, column)).sum();
    }

    public long grandTotal() {
        return rows.stream().mapToLong(this::rowTotal).sum();
    }
}
