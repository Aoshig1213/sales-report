package io.github.aoshig1213.salesreport.reader;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * CSV の文字コードを判定する。
 *
 * <p>Excel で「CSV（コンマ区切り）」保存すると Shift_JIS（Windows-31J）、
 * 「CSV UTF-8」で保存すると BOM 付き UTF-8 になる。どちらも受け付けられるように、
 * BOM があれば UTF-8、UTF-8 として正しく読めれば UTF-8、それ以外は Windows-31J とみなす。
 */
public final class CharsetDetector {

    public static final Charset WINDOWS_31J = Charset.forName("Windows-31J");

    private CharsetDetector() {
    }

    public static Charset detect(byte[] bytes) {
        if (hasUtf8Bom(bytes)) {
            return StandardCharsets.UTF_8;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return WINDOWS_31J;
        }
    }

    public static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF;
    }
}
