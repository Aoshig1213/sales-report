package io.github.aoshig1213.salesreport.aggregate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 帳票に出す集計結果一式。
 */
public record SalesReport(
        LocalDate from,
        LocalDate to,
        long totalAmount,
        long totalQuantity,
        int recordCount,
        List<YearMonth> months,
        Pivot<String, YearMonth> storeByMonth,
        List<StoreSummary> stores,
        List<ProductSummary> products,
        Pivot<String, YearMonth> categoryByMonth) {

    public record StoreSummary(String store, long amount, long quantity, int recordCount, double share) {
    }

    /**
     * @param cumulativeShare 1位からこの商品までの売上構成比の累計
     * @param abcRank         ABC分析の区分
     */
    public record ProductSummary(int rank, String productCode, String productName, String category,
            long quantity, long amount, double share, double cumulativeShare, AbcRank abcRank) {
    }

    /**
     * ABC分析の区分。売上の大きい順に並べ、累計構成比で分ける。
     * A: 売上の約7割を占める主力商品、B: 次の2割、C: 残り
     */
    public enum AbcRank {
        A, B, C;

        /** @param shareBefore この商品より上位の商品の累計構成比 */
        static AbcRank of(double shareBefore) {
            if (shareBefore < 0.7) {
                return A;
            }
            return shareBefore < 0.9 ? B : C;
        }
    }
}
