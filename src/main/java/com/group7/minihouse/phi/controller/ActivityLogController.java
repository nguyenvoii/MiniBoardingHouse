package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ActivityLogResponse;
import com.group7.minihouse.phi.service.ActivityLogService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import java.time.LocalDateTime;
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

    public ActivityLogController(ActivityLogService service) {
        this.service = service;
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

    /** Nhat ky cua chinh minh (moi nguoi da dang nhap, ke ca nguoi thue). */
    @GetMapping("/api/auth/activity")
    public ApiResponse<PageResponse<ActivityLogResponse>> mine(@AuthenticationPrincipal AuthUser user,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.mine(CurrentUser.requireUserId(user), page, size));
    }
}
