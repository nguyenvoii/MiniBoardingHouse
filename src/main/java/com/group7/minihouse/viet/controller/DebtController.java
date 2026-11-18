package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.viet.dto.DebtDtos.DebtDetailResponse;
import com.group7.minihouse.viet.dto.DebtDtos.DebtResponse;
import com.group7.minihouse.viet.dto.DebtDtos.DebtSummaryResponse;
import com.group7.minihouse.viet.dto.DebtDtos.ProcessingLogRequest;
import com.group7.minihouse.viet.dto.DebtDtos.ProcessingLogResponse;
import com.group7.minihouse.viet.dto.DebtDtos.RecalcResult;
import com.group7.minihouse.viet.dto.DebtDtos.ReminderRunResult;
import com.group7.minihouse.viet.service.DebtService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC93-100: cong no (phia nhan vien). Doc: LANDLORD, MANAGER, CASHIER. Ghi/nhac/tinh lai: LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/debts")
public class DebtController {

    private static final String READ = "hasAnyRole('LANDLORD','MANAGER','CASHIER')";
    private static final String WRITE = "hasAnyRole('LANDLORD','MANAGER')";

    private final DebtService service;

    public DebtController(DebtService service) {
        this.service = service;
    }

    /** Loc: status (NORMAL/WARNING_OVERDUE/LONG_TERM_DEBT/CLEARED), roomId, tenantId; phan trang (size toi da 100). */
    @GetMapping
    @PreAuthorize(READ)
    public ApiResponse<PageResponse<DebtResponse>> search(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Integer roomId,
                                                          @RequestParam(required = false) Integer tenantId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(status, roomId, tenantId, page, size));
    }

    /** Tong cong no toan khu va so luong theo trang thai (UC97). */
    @GetMapping("/summary")
    @PreAuthorize(READ)
    public ApiResponse<DebtSummaryResponse> summary() {
        return ApiResponse.ok(service.summary());
    }

    /** UC98: tai file Excel danh sach cong no (loc theo status neu co). */
    @GetMapping("/export")
    @PreAuthorize(READ)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String status) {
        byte[] file = service.exportExcel(status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cong-no.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ApiResponse<DebtDetailResponse> detail(@PathVariable Integer id) {
        return ApiResponse.ok(service.detail(id));
    }

    /** Tinh lai cong no + tien phat tu cac hoa don chua tra (UC95, UC96). */
    @PostMapping("/recalculate")
    @PreAuthorize(READ) // L3: thu ngan mo trang Cong no cung lam moi duoc so lieu (idempotent), khong bi 403 nuot im
    public ApiResponse<RecalcResult> recalculate() {
        return ApiResponse.ok(service.recalculate());
    }

    /** Chay nhac no ngay (UC99); job tu dong cung goi ham nay moi sang. */
    @PostMapping("/reminders/run")
    @PreAuthorize(WRITE)
    public ApiResponse<ReminderRunResult> runReminders() {
        return ApiResponse.ok(service.sendReminders());
    }

    /** UC100: ghi nhan cam ket thanh toan, ly do cham, ghi chu. */
    @PostMapping("/{id}/processing-logs")
    @PreAuthorize(WRITE)
    public ApiResponse<ProcessingLogResponse> addLog(@PathVariable Integer id, @RequestBody ProcessingLogRequest req,
                                                     @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.ok(service.addProcessingLog(id, req, actor == null ? null : actor.userId()));
    }

    @GetMapping("/{id}/processing-logs")
    @PreAuthorize(READ)
    public ApiResponse<List<ProcessingLogResponse>> logs(@PathVariable Integer id) {
        return ApiResponse.ok(service.processingLogs(id));
    }
}
