package com.group7.minihouse.shared.export;

import com.group7.minihouse.shared.exception.BusinessException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Khung xuat Excel (.xlsx) va PDF dung chung. Cac nhom khac chi can dung TableData roi goi toExcel/toPdf,
 * va tra ve trong controller voi EXCEL_TYPE / PDF_TYPE (xem ActivityLogController.export lam mau).
 * PDF can font Unicode de hien tieng Viet co dau: tim theo thu tu app.export.pdf-font, Arial (Windows/Mac),
 * DejaVu Sans (Linux); khong co thi dung font mac dinh (mat dau tieng Viet).
 */
@Component
public class TableExporter {

    public static final String EXCEL_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String PDF_TYPE = "application/pdf";
    public static final int MAX_ROWS = 50_000;

    private static final Logger log = LoggerFactory.getLogger(TableExporter.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/arial.ttf", "/Library/Fonts/Arial.ttf", "/System/Library/Fonts/Supplemental/Arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/TTF/DejaVuSans.ttf");

    private final String configuredFont;

    public TableExporter(@Value("${app.export.pdf-font:}") String configuredFont) {
        this.configuredFont = configuredFont == null ? "" : configuredFont.trim();
    }

    public byte[] toExcel(TableData data) {
        checkSize(data);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(data.title()));
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row hr = sheet.createRow(0);
            for (int c = 0; c < data.headers().size(); c++) {
                Cell cell = hr.createCell(c);
                cell.setCellValue(data.headers().get(c));
                cell.setCellStyle(head);
            }
            int r = 1;
            for (List<Object> row : data.rows()) {
                Row xr = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    Object v = row.get(c);
                    Cell cell = xr.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(text(v));
                    }
                }
            }
            for (int c = 0; c < data.headers().size(); c++) {
                sheet.autoSizeColumn(c);
                if (sheet.getColumnWidth(c) > 60 * 256) sheet.setColumnWidth(c, 60 * 256);
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] toPdf(TableData data) {
        checkSize(data);
        BaseFont base = loadFont();
        Font title = new Font(base, 14, Font.BOLD);
        Font head = new Font(base, 9, Font.BOLD);
        Font body = new Font(base, 9);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(data.headers().size() > 5 ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph(data.title(), title));
        doc.add(new Paragraph(" ", body));
        PdfPTable table = new PdfPTable(data.headers().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String h : data.headers()) {
            PdfPCell cell = new PdfPCell(new Phrase(h, head));
            cell.setBackgroundColor(new Color(220, 220, 220));
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (List<Object> row : data.rows()) {
            for (Object v : row) {
                PdfPCell cell = new PdfPCell(new Phrase(text(v), body));
                cell.setPadding(3);
                if (v instanceof Number) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cell);
            }
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ noi bo

    private static void checkSize(TableData data) {
        if (data.rows().size() > MAX_ROWS) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Qua nhieu dong de xuat (toi da " + MAX_ROWS + "), hay loc bot du lieu");
        }
    }

    private static String text(Object v) {
        if (v == null) return "";
        if (v instanceof LocalDateTime t) return t.format(DATE_TIME);
        if (v instanceof LocalDate d) return d.format(DATE);
        return v.toString();
    }

    private BaseFont loadFont() {
        List<String> paths = new java.util.ArrayList<>();
        if (!configuredFont.isEmpty()) paths.add(configuredFont);
        paths.addAll(FONT_CANDIDATES);
        for (String p : paths) {
            if (Files.isRegularFile(Path.of(p))) {
                try {
                    return BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (Exception e) {
                    log.warn("Khong dung duoc font {}: {}", p, e.getMessage());
                }
            }
        }
        log.warn("Khong tim thay font Unicode, PDF se mat dau tieng Viet. Dat app.export.pdf-font=duong/dan/font.ttf");
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
