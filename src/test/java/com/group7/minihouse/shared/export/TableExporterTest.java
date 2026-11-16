package com.group7.minihouse.shared.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group7.minihouse.shared.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Khung xuat bang du lieu ra Excel/PDF dung chung cho bao cao (UC116, 118, danh sach...). */
class TableExporterTest {

    final TableExporter exporter = new TableExporter("");

    private TableData sample() {
        return new TableData("Báo cáo thử: nhà trọ [A]/B", List.of("Mã", "Họ tên", "Số tiền", "Thời điểm"),
                List.of(Arrays.asList(1, "Nguyễn Văn A", 1500000.5, LocalDateTime.of(2026, 11, 16, 8, 30)),
                        Arrays.asList(2, null, 0, null)));
    }

    @Test
    void excelHasHeaderRowsAndKeepsVietnamese() throws Exception {
        byte[] bytes = exporter.toExcel(sample());

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals("Họ tên", sheet.getRow(0).getCell(1).getStringCellValue());
            assertEquals("Nguyễn Văn A", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(1500000.5, sheet.getRow(1).getCell(2).getNumericCellValue());
            assertEquals("16/11/2026 08:30", sheet.getRow(1).getCell(3).getStringCellValue());
            assertEquals("", sheet.getRow(2).getCell(1).getStringCellValue()); // null -> o trong
            assertEquals(3, sheet.getPhysicalNumberOfRows());
            assertTrue(sheet.getSheetName().length() <= 31);
            assertTrue(!sheet.getSheetName().matches(".*[\\[\\]:*?/\\\\].*"), sheet.getSheetName());
        }
    }

    @Test
    void pdfIsAValidPdfDocument() {
        byte[] bytes = exporter.toPdf(sample());

        assertTrue(bytes.length > 500);
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void emptyTableStillExports() {
        TableData empty = new TableData("Rỗng", List.of("A", "B"), List.of());
        assertTrue(exporter.toExcel(empty).length > 0);
        assertEquals("%PDF", new String(exporter.toPdf(empty), 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void rejectsTooManyRowsAndRaggedRows() {
        List<List<Object>> many = new ArrayList<>();
        for (int i = 0; i <= TableExporter.MAX_ROWS; i++) many.add(List.of(i));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                () -> exporter.toExcel(new TableData("x", List.of("A"), many))).getStatus());

        assertThrows(IllegalArgumentException.class,
                () -> new TableData("x", List.of("A", "B"), List.of(List.of(1))));
    }

    @Test
    void contentTypesAndFileNames() {
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", TableExporter.EXCEL_TYPE);
        assertEquals("application/pdf", TableExporter.PDF_TYPE);
    }
}
