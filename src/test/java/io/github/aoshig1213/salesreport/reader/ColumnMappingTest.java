package io.github.aoshig1213.salesreport.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ColumnMappingTest {

    @Test
    void 並び順が違っても別名でも列を見つける() {
        ColumnMapping m = ColumnMapping.fromHeader(List.of("数量", "品番", "売上日", " 店舗名 ", "品名", "販売単価"));
        List<String> row = List.of("3", "D-001", "2026/04/01", "梅田店", "緑茶", "150");

        assertThat(m.get(row, Column.DATE)).contains("2026/04/01");
        assertThat(m.get(row, Column.STORE)).contains("梅田店");
        assertThat(m.get(row, Column.QUANTITY)).contains("3");
        assertThat(m.get(row, Column.CATEGORY)).isEmpty();
    }

    @Test
    void 英語の見出しも読める() {
        ColumnMapping m = ColumnMapping.fromHeader(
                List.of("Date", "Store", "Product_Code", "Product_Name", "Unit_Price", "Quantity"));

        assertThat(m.get(List.of("2026-04-01", "A", "X", "Y", "100", "1"), Column.UNIT_PRICE)).contains("100");
    }

    @Test
    void 必須列がなければ足りない列名を知らせる() {
        assertThatThrownBy(() -> ColumnMapping.fromHeader(List.of("日付", "店舗", "商品名", "数量")))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("商品コード")
                .hasMessageContaining("単価");
    }
}
