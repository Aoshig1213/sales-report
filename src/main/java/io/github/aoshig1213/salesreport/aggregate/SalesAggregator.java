package io.github.aoshig1213.salesreport.aggregate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aoshig1213.salesreport.aggregate.SalesReport.AbcRank;
import io.github.aoshig1213.salesreport.aggregate.SalesReport.ProductSummary;
import io.github.aoshig1213.salesreport.aggregate.SalesReport.StoreSummary;
import io.github.aoshig1213.salesreport.model.SalesRecord;

public class SalesAggregator {

    /**
     * @throws IllegalArgumentException 明細が0件のとき
     */
    public SalesReport aggregate(List<SalesRecord> records) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("集計できる明細がありません");
        }

        LocalDate from = records.stream().map(SalesRecord::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = records.stream().map(SalesRecord::date).max(Comparator.naturalOrder()).orElseThrow();
        long totalAmount = records.stream().mapToLong(SalesRecord::amount).sum();
        long totalQuantity = records.stream().mapToLong(SalesRecord::quantity).sum();
        List<YearMonth> months = monthsBetween(YearMonth.from(from), YearMonth.from(to));

        return new SalesReport(
                from, to, totalAmount, totalQuantity, records.size(), months,
                Pivot.of(records, SalesRecord::store, SalesRecord::month, SalesRecord::amount, months),
                stores(records, totalAmount),
                products(records, totalAmount),
                Pivot.of(records, SalesRecord::category, SalesRecord::month, SalesRecord::amount, months));
    }

    /** 売上がない月も列として出すため、最初の月から最後の月まで全部並べる */
    private static List<YearMonth> monthsBetween(YearMonth first, YearMonth last) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private static List<StoreSummary> stores(List<SalesRecord> records, long totalAmount) {
        Map<String, List<SalesRecord>> byStore = records.stream()
                .collect(Collectors.groupingBy(SalesRecord::store, LinkedHashMap::new, Collectors.toList()));
        return byStore.entrySet().stream()
                .map(e -> {
                    long amount = e.getValue().stream().mapToLong(SalesRecord::amount).sum();
                    long quantity = e.getValue().stream().mapToLong(SalesRecord::quantity).sum();
                    return new StoreSummary(e.getKey(), amount, quantity, e.getValue().size(),
                            share(amount, totalAmount));
                })
                .sorted(Comparator.comparingLong(StoreSummary::amount).reversed())
                .toList();
    }

    /** 商品コードごとに集計し、売上順に並べて ABC 区分を付ける */
    private static List<ProductSummary> products(List<SalesRecord> records, long totalAmount) {
        Map<String, List<SalesRecord>> byCode = records.stream()
                .collect(Collectors.groupingBy(SalesRecord::productCode, LinkedHashMap::new, Collectors.toList()));

        record Row(SalesRecord first, long quantity, long amount) {
        }
        List<Row> rows = byCode.values().stream()
                .map(list -> new Row(list.getFirst(),
                        list.stream().mapToLong(SalesRecord::quantity).sum(),
                        list.stream().mapToLong(SalesRecord::amount).sum()))
                .sorted(Comparator.comparingLong(Row::amount).reversed()
                        .thenComparing(r -> r.first().productCode()))
                .toList();

        List<ProductSummary> result = new ArrayList<>(rows.size());
        long cumulative = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            double shareBefore = share(cumulative, totalAmount);
            cumulative += r.amount();
            result.add(new ProductSummary(i + 1, r.first().productCode(), r.first().productName(),
                    r.first().category(), r.quantity(), r.amount(), share(r.amount(), totalAmount),
                    share(cumulative, totalAmount), AbcRank.of(shareBefore)));
        }
        return result;
    }

    private static double share(long part, long total) {
        return total == 0 ? 0 : (double) part / total;
    }
}
