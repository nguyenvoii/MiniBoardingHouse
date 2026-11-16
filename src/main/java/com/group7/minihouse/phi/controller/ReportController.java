package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ReportDtos.Dashboard;
import com.group7.minihouse.phi.dto.ReportDtos.RevenueReport;
import com.group7.minihouse.phi.service.ReportService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC116 dashboard va UC118 bao cao doanh thu - chi phi (LANDLORD, MANAGER). propertyId bo trong = tat ca khu tro. */
@RestController
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class ReportController {

    private final ReportService service;
    private final TableExporter exporter;

    public ReportController(ReportService service, TableExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    @GetMapping("/api/dashboard")
    public ApiResponse<Dashboard> dashboard(@RequestParam(required = false) Integer propertyId) {
        return ApiResponse.ok(service.dashboard(propertyId));
    }

    @GetMapping("/api/reports/revenue")
    public ApiResponse<RevenueReport> revenue(@RequestParam(required = false) Integer propertyId,
                                              @RequestParam(required = false) Integer year) {
        return ApiResponse.ok(service.revenueReport(propertyId, year));
    }

    /** format=xlsx (mac dinh) hoac pdf. */
    @GetMapping("/api/reports/revenue/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer propertyId,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "format chi nhan xlsx hoac pdf");
        }
        RevenueReport r = service.revenueReport(propertyId, year);
        var table = ReportService.toTable(r);
        byte[] body = pdf ? exporter.toPdf(table) : exporter.toExcel(table);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pdf ? TableExporter.PDF_TYPE : TableExporter.EXCEL_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"doanh-thu-" + r.year() + "." + (pdf ? "pdf" : "xlsx") + "\"")
                .body(body);
    }
}
