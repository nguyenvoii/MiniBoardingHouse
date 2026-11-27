package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.service.ActivityLogService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.monitoring.SystemStatusService;
import com.group7.minihouse.shared.monitoring.SystemStatusService.Status;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trang tinh trang may chu: chi chu nha (thong tin ha tang). /api/ping van cong khai cho viec kiem tra song/chet. */
@RestController
@PreAuthorize("hasRole('LANDLORD')")
public class SystemStatusController {

    private final SystemStatusService service;
    private final ActivityLogService activityLogService;

    public SystemStatusController(SystemStatusService service, ActivityLogService activityLogService) {
        this.service = service;
        this.activityLogService = activityLogService;
    }

    @GetMapping("/api/admin/system/status")
    public ApiResponse<Status> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/api/admin/system/cache/clear")
    public ApiResponse<Integer> clearCache(@AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        int n = service.clearCaches();
        if (user != null) {
            activityLogService.log(user.userId(), "CLEAR_CACHE", "Xóa bộ nhớ đệm (" + n + " nhóm)", http.getRemoteAddr());
        }
        return ApiResponse.ok("Đã xóa bộ nhớ đệm", n);
    }
}
