package io.github.aoshig1213.salesreport.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CharsetDetectorTest {

    private static final String TEXT = "日付,店舗,商品名\n2026/04/01,渋谷店,緑茶\n";

    @Test
    void BOM付きUTF8() {
        byte[] body = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);

        assertThat(CharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void BOMなしUTF8() {
        assertThat(CharsetDetector.detect(TEXT.getBytes(StandardCharsets.UTF_8))).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void ShiftJIS() {
        assertThat(CharsetDetector.detect(TEXT.getBytes(CharsetDetector.WINDOWS_31J)))
                .isEqualTo(CharsetDetector.WINDOWS_31J);
    }

    @Test
    void 機種依存文字を含むShiftJIS() {
        // ①や㈱は Shift_JIS にはなく Windows-31J にある。Excel の CSV にはよく入っている
        String text = "商品名\n①番人気の㈱テスト商品\n";

        byte[] bytes = text.getBytes(CharsetDetector.WINDOWS_31J);

        assertThat(new String(bytes, CharsetDetector.detect(bytes))).isEqualTo(text);
    }
}
