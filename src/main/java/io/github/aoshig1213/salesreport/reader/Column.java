package io.github.aoshig1213.salesreport.reader;

import java.util.List;

/**
 * 入力ファイルの列。
 * システムや担当者によって見出しの書き方が違うので、よくある別名も同じ列として扱う。
 */
public enum Column {

    DATE(true, "日付", "売上日", "販売日", "date"),
    STORE(true, "店舗", "店舗名", "店名", "store"),
    PRODUCT_CODE(true, "商品コード", "品番", "JANコード", "product_code"),
    PRODUCT_NAME(true, "商品名", "品名", "product_name"),
    CATEGORY(false, "カテゴリ", "カテゴリー", "分類", "category"),
    UNIT_PRICE(true, "単価", "販売単価", "unit_price"),
    QUANTITY(true, "数量", "個数", "販売数", "quantity"),
    /** 省略可。ある場合は 単価 × 数量 と一致するかチェックする */
    AMOUNT(false, "金額", "売上金額", "売上", "amount");

    private final boolean required;
    private final List<String> aliases;

    Column(boolean required, String... aliases) {
        this.required = required;
        this.aliases = List.of(aliases);
    }

    public boolean isRequired() {
        return required;
    }

    /** 画面・エラー表示用の名前 */
    public String label() {
        return aliases.getFirst();
    }

    boolean matches(String header) {
        String normalized = normalize(header);
        return aliases.stream().anyMatch(a -> normalize(a).equals(normalized));
    }

    /** 前後の空白、全角スペース、大文字小文字の違いを無視する */
    private static String normalize(String s) {
        return s.replace('　', ' ').strip().toLowerCase();
    }
}
