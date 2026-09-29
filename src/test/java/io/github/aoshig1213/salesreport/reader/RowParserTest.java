package io.github.aoshig1213.salesreport.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.aoshig1213.salesreport.model.SalesRecord;

class RowParserTest {

    private static final List<String> HEADER = List.of("日付", "店舗", "商品コード", "商品名", "カテゴリ", "単価", "数量", "金額");

    private final RowParser parser = new RowParser(ColumnMapping.fromHeader(HEADER), "test.csv");

    private SalesRecord parse(String... values) {
        return parser.parse(Arrays.asList(values), 2);
    }

    @Test
    void 正しい行を読める() {
        SalesRecord r = parse("2026/04/01", "渋谷店", "D-001", "緑茶", "飲料", "150", "3", "450");

        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.store()).isEqualTo("渋谷店");
        assertThat(r.amount()).isEqualTo(450);
        assertThat(r.sourceFile()).isEqualTo("test.csv");
        assertThat(r.sourceRow()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026/04/01", "2026/4/1", "2026-04-01", "2026年4月1日", "20260401" })
    void よくある日付の書き方を読める(String date) {
        assertThat(parse(date, "渋谷店", "D-001", "緑茶", "飲料", "150", "1", "").date())
                .isEqualTo(LocalDate.of(2026, 4, 1));
    }

    @Test
    void 桁区切りや円記号や全角数字の金額を読める() {
        SalesRecord r = parse("2026/04/01", "渋谷店", "D-003", "コーヒー", "飲料", "¥1,200", "３", "3,600円");

        assertThat(r.unitPrice()).isEqualTo(1200);
        assertThat(r.quantity()).isEqualTo(3);
        assertThat(r.amount()).isEqualTo(3600);
    }

    @Test
    void 金額列が空なら単価と数量から計算する() {
        assertThat(parse("2026/04/01", "渋谷店", "D-001", "緑茶", "飲料", "150", "4", "").amount()).isEqualTo(600);
    }

    @Test
    void マイナスの数量は返品として読む() {
        assertThat(parse("2026/04/01", "渋谷店", "D-001", "緑茶", "飲料", "150", "-1", "-150").amount()).isEqualTo(-150);
    }

    @Test
    void カテゴリが空なら未分類にする() {
        assertThat(parse("2026/04/01", "渋谷店", "D-001", "緑茶", "", "150", "1", "").category()).isEqualTo("（未分類）");
    }

    @Test
    void 存在しない日付はエラー() {
        assertThatThrownBy(() -> parse("2026/04/31", "渋谷店", "D-001", "緑茶", "飲料", "150", "1", ""))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("日付「2026/04/31」");
    }

    @Test
    void 金額が単価と数量に合わなければエラー() {
        assertThatThrownBy(() -> parse("2026/04/01", "渋谷店", "D-001", "緑茶", "飲料", "150", "3", "460"))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("一致しません");
    }

    @Test
    void 問題が複数あればまとめて知らせる() {
        assertThatThrownBy(() -> parse("", "渋谷店", "", "緑茶", "飲料", "abc", "0", ""))
                .isInstanceOf(InvalidRowException.class)
                .hasMessageContaining("日付が空です")
                .hasMessageContaining("商品コードが空です")
                .hasMessageContaining("単価「abc」が整数ではありません")
                .hasMessageContaining("数量が0");
    }
}
