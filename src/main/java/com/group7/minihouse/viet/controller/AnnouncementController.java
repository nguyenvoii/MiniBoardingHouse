package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementDetail;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementSendResult;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementSummary;
import com.group7.minihouse.viet.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.group7.minihouse.viet.service.AnnouncementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC130: thông báo chung tới người thuê và theo dõi người đã đọc. Chỉ LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/announcements")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class AnnouncementController {

    private final AnnouncementService service;

    public AnnouncementController(AnnouncementService service) {
        this.service = service;
    }

    /** UC130: gửi thông báo cho cả khu (scope=ALL) hoặc theo tầng (scope=FLOOR kèm floorIds). */
    @PostMapping
    public ApiResponse<AnnouncementSendResult> send(@Valid @RequestBody CreateAnnouncementRequest req) {
        AnnouncementSendResult result = service.send(req);
        return ApiResponse.ok("Đã gửi thông báo cho " + result.sent() + "/" + result.recipients() + " người thuê",
                result);
    }

    /** UC130: các đợt thông báo đã gửi, kèm số người đã đọc. */
    @GetMapping
    public ApiResponse<PageResponse<AnnouncementSummary>> list(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(page, size));
    }

    /** UC130: chi tiết một đợt, ai đã đọc và ai chưa. */
    @GetMapping("/{batchId}")
    public ApiResponse<AnnouncementDetail> detail(@PathVariable Integer batchId) {
        return ApiResponse.ok(service.detail(batchId));
    }
}
