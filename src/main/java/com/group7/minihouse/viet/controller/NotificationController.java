package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.viet.dto.NotificationDtos.AffectedCount;
import com.group7.minihouse.viet.dto.NotificationDtos.NotificationResponse;
import com.group7.minihouse.viet.dto.NotificationDtos.UnreadCount;
import com.group7.minihouse.viet.service.NotificationCenterService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC123: trung tam thong bao cua chinh nguoi dang nhap. Nguoi thue dung /api/me/notifications,
 * nhan vien dung /api/notifications (khi bat bao mat, /api/me/** chi nguoi thue vao duoc, cac duong dan khac
 * chi nhan vien). Can token: khi tat bao mat (che do dev) khong co nguoi dung nen se nhan 401.
 */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationCenterService service;

    public NotificationController(NotificationCenterService service) {
        this.service = service;
    }

    @GetMapping({"/me/notifications", "/notifications"})
    public ApiResponse<PageResponse<NotificationResponse>> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.list(CurrentUser.requireUserId(user), unreadOnly, page, size));
    }

    @GetMapping({"/me/notifications/unread-count", "/notifications/unread-count"})
    public ApiResponse<UnreadCount> unreadCount(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(new UnreadCount(service.unreadCount(CurrentUser.requireUserId(user))));
    }

    @PatchMapping({"/me/notifications/{id}/read", "/notifications/{id}/read"})
    public ApiResponse<NotificationResponse> markRead(@PathVariable Integer id,
                                                      @AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.markRead(CurrentUser.requireUserId(user), id));
    }

    @PatchMapping({"/me/notifications/read-all", "/notifications/read-all"})
    public ApiResponse<AffectedCount> markAllRead(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(new AffectedCount(service.markAllRead(CurrentUser.requireUserId(user))));
    }

    /** Xoa tat ca thong bao da doc. */
    @DeleteMapping({"/me/notifications/read", "/notifications/read"})
    public ApiResponse<AffectedCount> deleteRead(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(new AffectedCount(service.deleteRead(CurrentUser.requireUserId(user))));
    }

    @DeleteMapping({"/me/notifications/{id}", "/notifications/{id}"})
    public ApiResponse<Void> delete(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user) {
        service.delete(CurrentUser.requireUserId(user), id);
        return ApiResponse.ok("Da xoa thong bao", null);
    }
}
