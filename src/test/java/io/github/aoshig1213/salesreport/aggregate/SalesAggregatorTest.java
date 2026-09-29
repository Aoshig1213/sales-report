package io.github.aoshig1213.salesreport.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.aoshig1213.salesreport.aggregate.SalesReport.AbcRank;
import io.github.aoshig1213.salesreport.aggregate.SalesReport.ProductSummary;
import io.github.aoshig1213.salesreport.model.SalesRecord;

class SalesAggregatorTest {

    private final SalesAggregator aggregator = new SalesAggregator();

    private static SalesRecord sale(String date, String store, String code, String category, long price, int qty) {
        return new SalesRecord(LocalDate.parse(date), store, code, code + "の名前", category, price, qty,
                price * qty, "f.csv", 2);
    }

    @Test
    void 店舗別と月別に集計する() {
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "A", "飲料", 100, 1),
                sale("2026-04-15", "渋谷店", "A", "飲料", 100, 2),
                sale("2026-05-01", "梅田店", "B", "菓子", 500, 1)));

        Pivot<String, YearMonth> pivot = report.storeByMonth();
        assertThat(pivot.get("渋谷店", YearMonth.of(2026, 4))).isEqualTo(300);
        assertThat(pivot.get("渋谷店", YearMonth.of(2026, 5))).isZero();
        assertThat(pivot.columnTotal(YearMonth.of(2026, 5))).isEqualTo(500);
        assertThat(pivot.grandTotal()).isEqualTo(800);
        assertThat(pivot.rows()).containsExactly("梅田店", "渋谷店"); // 売上の大きい順
        assertThat(report.totalAmount()).isEqualTo(800);
        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(report.to()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void 売上がない月も列に出す() {
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "A", "飲料", 100, 1),
                sale("2026-06-01", "渋谷店", "A", "飲料", 100, 1)));

        assertThat(report.months()).containsExactly(
                YearMonth.of(2026, 4), YearMonth.of(2026, 5), YearMonth.of(2026, 6));
    }

    @Test
    void 店舗の構成比() {
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "A", "飲料", 300, 1),
                sale("2026-04-01", "梅田店", "A", "飲料", 100, 1)));

        assertThat(report.stores()).extracting(SalesReport.StoreSummary::store).containsExactly("渋谷店", "梅田店");
        assertThat(report.stores().getFirst().share()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    void 返品は売上から差し引く() {
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "A", "飲料", 100, 3),
                sale("2026-04-02", "渋谷店", "A", "飲料", 100, -1)));

        assertThat(report.totalAmount()).isEqualTo(200);
        assertThat(report.products().getFirst().quantity()).isEqualTo(2);
    }

    @Test
    void 商品を売上順に並べてABC区分を付ける() {
        // 構成比 60% / 20% / 12% / 8%
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "P1", "飲料", 600, 1),
                sale("2026-04-01", "渋谷店", "P2", "飲料", 200, 1),
                sale("2026-04-01", "渋谷店", "P3", "菓子", 120, 1),
                sale("2026-04-01", "渋谷店", "P4", "菓子", 80, 1)));

        List<ProductSummary> products = report.products();
        assertThat(products).extracting(ProductSummary::productCode).containsExactly("P1", "P2", "P3", "P4");
        assertThat(products).extracting(ProductSummary::rank).containsExactly(1, 2, 3, 4);
        assertThat(products.get(1).cumulativeShare()).isCloseTo(0.8, within(1e-9));
        // P1 より上は 0% → A、P2 より上は 60% → A、P3 より上は 80% → B、P4 より上は 92% → C
        assertThat(products).extracting(ProductSummary::abcRank)
                .containsExactly(AbcRank.A, AbcRank.A, AbcRank.B, AbcRank.C);
    }

    @Test
    void 一つの商品で7割を超えてもA() {
        SalesReport report = aggregator.aggregate(List.of(
                sale("2026-04-01", "渋谷店", "P1", "飲料", 950, 1),
                sale("2026-04-01", "渋谷店", "P2", "飲料", 50, 1)));

        assertThat(report.products()).extracting(ProductSummary::abcRank).containsExactly(AbcRank.A, AbcRank.C);
    }
}
