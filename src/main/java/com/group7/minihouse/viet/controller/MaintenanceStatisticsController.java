package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceHistoryItem;
import com.group7.minihouse.viet.dto.MaintenanceStatsDtos.MaintenanceStatsResponse;
import com.group7.minihouse.viet.service.MaintenanceStatisticsService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC131: thống kê sự cố và lịch sử bảo trì theo phòng. Chỉ LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class MaintenanceStatisticsController {

    private final MaintenanceStatisticsService service;
    private final TableExporter exporter;

    public MaintenanceStatisticsController(MaintenanceStatisticsService service, TableExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    /** UC131: thống kê sự cố theo trạng thái, nhóm, tháng, phòng hay hỏng. from/to dạng 2026-11-01. */
    @GetMapping("/maintenance-requests/statistics")
    public ApiResponse<MaintenanceStatsResponse> statistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.statistics(from, to));
    }

    /** UC131: tải thống kê theo nhóm sự cố, format=xlsx (mặc định) hoặc pdf. */
    @GetMapping("/maintenance-requests/statistics/export")
    public ResponseEntity<byte[]> exportStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format) {
        return file(service.statisticsTable(from, to), format, "thong-ke-su-co");
    }

    /** UC131: lịch sử bảo trì của một phòng, kèm người xử lý, chi phí, điểm đánh giá. */
    @GetMapping("/rooms/{roomId}/maintenance-history")
    public ApiResponse<List<MaintenanceHistoryItem>> history(@PathVariable Integer roomId) {
        return ApiResponse.ok(service.roomHistory(roomId));
    }

    /** UC131: tải lịch sử bảo trì của một phòng, format=xlsx (mặc định) hoặc pdf. */
    @GetMapping("/rooms/{roomId}/maintenance-history/export")
    public ResponseEntity<byte[]> exportHistory(@PathVariable Integer roomId,
                                                @RequestParam(defaultValue = "xlsx") String format) {
        return file(service.historyTable(roomId), format, "lich-su-bao-tri-phong-" + roomId);
    }

    private ResponseEntity<byte[]> file(TableData data, String format, String baseName) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) {
            throw new BusinessException("format chỉ nhận xlsx hoặc pdf");
        }
        byte[] body = pdf ? exporter.toPdf(data) : exporter.toExcel(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pdf ? TableExporter.PDF_TYPE : TableExporter.EXCEL_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + baseName + (pdf ? ".pdf" : ".xlsx") + "\"")
                .body(body);
    }
}
