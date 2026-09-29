package io.github.aoshig1213.salesreport.reader;

import java.io.IOException;
import java.nio.file.Path;

import io.github.aoshig1213.salesreport.model.ReadResult;

public interface SalesFileReader {

    /**
     * 1ファイルを読む。不正な行があっても止めずに、エラーとして結果に含める。
     *
     * @throws IOException ファイル自体が開けないとき
     */
    ReadResult read(Path file) throws IOException;
}
