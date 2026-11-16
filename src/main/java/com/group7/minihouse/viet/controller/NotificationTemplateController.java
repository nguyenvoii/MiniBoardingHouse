package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.ActiveRequest;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplatePreview;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplateRequest;
import com.group7.minihouse.viet.dto.NotificationDtos.TemplateResponse;
import com.group7.minihouse.viet.service.NotificationTemplateService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC121: mau thong bao va kenh gui (EMAIL, SMS, SYSTEM_APP). Chi LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/notification-templates")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    public NotificationTemplateController(NotificationTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TemplateResponse>> search(@RequestParam(required = false) String code,
                                                      @RequestParam(required = false) String channel,
                                                      @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(service.search(code, channel, active));
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Noi dung mau dung bien dang {{ten_bien}}, vi du "Phong {{room}} con no {{amount}}". */
    @PostMapping
    public ApiResponse<TemplateResponse> create(@RequestBody TemplateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateResponse> update(@PathVariable Integer id, @RequestBody TemplateRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    /** Bat/tat mau: {"active": true|false}. */
    @PatchMapping("/{id}/active")
    public ApiResponse<TemplateResponse> setActive(@PathVariable Integer id, @RequestBody ActiveRequest req) {
        return ApiResponse.ok(service.setActive(id, req == null ? null : req.active()));
    }

    /** Xem truoc: body la map ten bien -> gia tri, vi du {"room":"P101","amount":"1.500.000 đ"}. */
    @PostMapping("/{id}/preview")
    public ApiResponse<TemplatePreview> preview(@PathVariable Integer id,
                                                @RequestBody(required = false) Map<String, String> params) {
        return ApiResponse.ok(service.preview(id, params));
    }
}
