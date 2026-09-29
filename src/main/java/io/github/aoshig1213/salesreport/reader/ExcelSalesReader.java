package io.github.aoshig1213.salesreport.reader;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import io.github.aoshig1213.salesreport.model.ReadResult;

/**
 * Excel（.xlsx / .xls）の先頭シートを読む。
 */
public class ExcelSalesReader implements SalesFileReader {

    @Override
    public ReadResult read(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        List<Integer> rowNumbers = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    values.add(toText(row.getCell(c)));
                }
                rows.add(values);
                rowNumbers.add(row.getRowNum() + 1);
            }
        }
        return RowsToRecords.convert(file.getFileName().toString(), rows, rowNumbers);
    }

    /**
     * セルを文字列にする。日付セルは yyyy-MM-dd、数値は「3.0」ではなく「3」にする。
     * 数式は計算済みの値を使う。
     */
    static String toText(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
