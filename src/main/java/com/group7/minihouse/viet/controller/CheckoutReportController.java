package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.viet.dto.CheckoutReportDtos.CheckoutReportResponse;
import com.group7.minihouse.viet.service.CheckoutReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC132: báo cáo hoàn tiền đặt cọc và quyết toán trả phòng theo tháng. Chỉ LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/checkout-reports")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class CheckoutReportController {

    private final CheckoutReportService service;
    private final TableExporter exporter;

    public CheckoutReportController(CheckoutReportService service, TableExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    /** UC132: xem báo cáo của một tháng, month dạng 2026-11. */
    @GetMapping
    public ApiResponse<CheckoutReportResponse> report(@RequestParam String month) {
        return ApiResponse.ok(service.report(month));
    }

    /** UC132: tải báo cáo, format=xlsx (mặc định) hoặc pdf. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String month,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) {
            throw new BusinessException("format chỉ nhận xlsx hoặc pdf");
        }
        TableData data = service.exportTable(month);
        byte[] body = pdf ? exporter.toPdf(data) : exporter.toExcel(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pdf ? TableExporter.PDF_TYPE : TableExporter.EXCEL_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"hoan-coc-" + month.trim() + (pdf ? ".pdf" : ".xlsx") + "\"")
                .body(body);
    }
}
