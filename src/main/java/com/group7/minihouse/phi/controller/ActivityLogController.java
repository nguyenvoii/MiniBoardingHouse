package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ActivityLogResponse;
import com.group7.minihouse.phi.service.ActivityLogService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.shared.export.TableExporter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC13: xem lich su dang nhap va nhat ky thao tac. */
@RestController
public class ActivityLogController {

    private final ActivityLogService service;
    private final TableExporter exporter;

    public ActivityLogController(ActivityLogService service, TableExporter exporter) {
        this.service = service;
        this.exporter = exporter;
    }

    /** Nhat ky cua moi nguoi dung (chi LANDLORD). from/to dinh dang 2026-11-14T08:00:00. */
    @GetMapping("/api/activity-logs")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<PageResponse<ActivityLogResponse>> search(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(userId, actionType, from, to, page, size));
    }

    /**
     * Xuat nhat ky ra file (toi da 5000 dong moi nhat). format=xlsx (mac dinh) hoac pdf; cac bo loc nhu /api/activity-logs.
     * Day cung la mau de nhom khac xuat bao cao: dung TableData + TableExporter.
     */
    @GetMapping("/api/activity-logs/export")
    @PreAuthorize("hasRole('LANDLORD')")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "xlsx") String format) {
        boolean pdf = "pdf".equalsIgnoreCase(format);
        if (!pdf && !"xlsx".equalsIgnoreCase(format)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "format chi nhan xlsx hoac pdf");
        }
        List<List<Object>> rows = service.exportRows(userId, actionType, from, to).stream()
                .map(l -> Arrays.<Object>asList(l.logId(), l.createdAt(), l.userFullName(), l.actionType(),
                        l.description(), l.ipAddress()))
                .toList();
        TableData data = new TableData("Nhật ký thao tác",
                List.of("Mã", "Thời điểm", "Người dùng", "Hành động", "Mô tả", "IP"), rows);
        byte[] body = pdf ? exporter.toPdf(data) : exporter.toExcel(data);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pdf ? TableExporter.PDF_TYPE : TableExporter.EXCEL_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"nhat-ky." + (pdf ? "pdf" : "xlsx") + "\"")
                .body(body);
    }

    /** Nhat ky cua chinh minh (moi nguoi da dang nhap, ke ca nguoi thue). */
    @GetMapping("/api/auth/activity")
    public ApiResponse<PageResponse<ActivityLogResponse>> mine(@AuthenticationPrincipal AuthUser user,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.mine(CurrentUser.requireUserId(user), page, size));
    }
}
