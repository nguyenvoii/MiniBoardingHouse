package com.group7.minihouse.toan.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.toan.dto.PaymentReportDtos.DailyPaymentReport;
import com.group7.minihouse.toan.service.PaymentReportService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC127: API báo cáo thu tiền và đối soát cuối ngày. */
@RestController
@RequestMapping("/api/payment-reports")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
public class PaymentReportController {
    private final PaymentReportService service;
    public PaymentReportController(PaymentReportService service) { this.service = service; }

    /** UC127: xem báo cáo thu tiền theo khoảng ngày. */
    @GetMapping("/daily")
    public ApiResponse<DailyPaymentReport> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.report(from, to));
    }

    /** UC127: tải báo cáo Excel để đối soát cuối ngày. */
    @GetMapping(value = "/daily/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] body = service.exportExcel(from, to);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bao-cao-thu-tien.xlsx").body(body);
    }
}
