package com.group7.minihouse.toan.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.toan.dto.MonthlyStatementDtos.MonthlyStatement;
import com.group7.minihouse.toan.service.MonthlyStatementService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC129: API bảng kê hóa đơn và phiếu thu theo tháng. */
@RestController
@RequestMapping("/api/monthly-statements")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
public class MonthlyStatementController {
    private final MonthlyStatementService service;
    public MonthlyStatementController(MonthlyStatementService service) { this.service = service; }

    /** UC129: xem bảng kê tháng, định dạng yyyy-MM. */
    @GetMapping
    public ApiResponse<MonthlyStatement> get(@RequestParam String month) {
        return ApiResponse.ok(service.get(month));
    }

    /** UC129: xuất bảng kê tháng ra Excel hoặc PDF. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String month,
                                         @RequestParam(defaultValue = "xlsx") String format) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] body = service.export(month, format);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(pdf ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=bang-ke-" + month + (pdf ? ".pdf" : ".xlsx"))
                .body(body);
    }
}
