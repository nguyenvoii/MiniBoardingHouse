package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.CreateStaffRequest;
import com.group7.minihouse.phi.dto.StaffCreatedResponse;
import com.group7.minihouse.phi.dto.StaffResponse;
import com.group7.minihouse.phi.dto.UpdateStaffRequest;
import com.group7.minihouse.phi.dto.UpdateStatusRequest;
import com.group7.minihouse.phi.service.StaffService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC8, UC9: quan ly tai khoan nhan vien. Ghi: chi LANDLORD. Doc/tim kiem: LANDLORD va MANAGER.
 * (@PreAuthorize chi co tac dung khi app.security.enabled=true.)
 */
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    /** Them nhan vien; tra ve mat khau tam (chi hien 1 lan). */
    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<StaffCreatedResponse> create(@RequestBody CreateStaffRequest req,
                                                    @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok(service.create(req, actorId(actor), http.getRemoteAddr()));
    }

    /** Tim kiem nhan vien: keyword (ten/email/SDT), status (ACTIVE/LOCKED/INACTIVE), phan trang (size toi da 100). */
    @GetMapping
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<PageResponse<StaffResponse>> search(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(keyword, status, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<StaffResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Sua thong tin; roles khac null thi thay toan bo vai tro nhan vien. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<StaffResponse> update(@PathVariable Integer id, @RequestBody UpdateStaffRequest req,
                                             @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok(service.update(id, req, actorId(actor), http.getRemoteAddr()));
    }

    /** Khoa (LOCKED) hoac mo khoa (ACTIVE); khoa co hieu luc ngay voi token da cap. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<StaffResponse> setStatus(@PathVariable Integer id, @RequestBody UpdateStatusRequest req,
                                                @AuthenticationPrincipal AuthUser actor, HttpServletRequest http) {
        return ApiResponse.ok(service.setStatus(id, req.status(), actorId(actor), http.getRemoteAddr()));
    }

    private static Integer actorId(AuthUser actor) {
        return actor == null ? null : actor.userId();
    }
}
