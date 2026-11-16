package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.viet.dto.ContractDtos.ExpiringContract;
import com.group7.minihouse.viet.dto.ContractDtos.StatusRefreshResult;
import com.group7.minihouse.viet.dto.EquipmentDtos.ActiveRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.AddonFeeResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.CategoryResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.EquipmentResponse;
import com.group7.minihouse.viet.dto.EquipmentDtos.InstallRequest;
import com.group7.minihouse.viet.dto.EquipmentDtos.ReviewRequest;
import com.group7.minihouse.viet.service.ContractExpiryService;
import com.group7.minihouse.viet.service.EquipmentService;
import com.group7.minihouse.viet.service.IncidentCategoryService;
import java.util.List;
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

/** UC113 (nhom su co/thiet bi), yeu cau thiet bi + phu phi phong, UC52 (hop dong sap het han). Chi LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class EquipmentController {

    private final IncidentCategoryService categoryService;
    private final EquipmentService equipmentService;
    private final ContractExpiryService contractExpiryService;

    public EquipmentController(IncidentCategoryService categoryService, EquipmentService equipmentService,
                               ContractExpiryService contractExpiryService) {
        this.categoryService = categoryService;
        this.equipmentService = equipmentService;
        this.contractExpiryService = contractExpiryService;
    }

    // ----- UC113: danh muc nhom su co / thiet bi -----

    @GetMapping("/incident-categories")
    public ApiResponse<List<CategoryResponse>> categories(@RequestParam(required = false) String type,
                                                          @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(categoryService.search(type, active));
    }

    @PostMapping("/incident-categories")
    public ApiResponse<CategoryResponse> createCategory(@RequestBody CategoryRequest req) {
        return ApiResponse.ok(categoryService.create(req));
    }

    @PutMapping("/incident-categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Integer id, @RequestBody CategoryRequest req) {
        return ApiResponse.ok(categoryService.update(id, req));
    }

    /** Bat/tat nhom: {"active": true|false}. */
    @PatchMapping("/incident-categories/{id}/active")
    public ApiResponse<CategoryResponse> setCategoryActive(@PathVariable Integer id, @RequestBody ActiveRequest req) {
        return ApiResponse.ok(categoryService.setActive(id, req == null ? null : req.active()));
    }

    // ----- Yeu cau thiet bi, phu phi phong -----

    @GetMapping("/equipment-requests")
    public ApiResponse<PageResponse<EquipmentResponse>> equipmentRequests(@RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) Integer roomId,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(equipmentService.search(status, roomId, page, size));
    }

    /** Duyet: {"approve": true|false, "note": "..."}. */
    @PatchMapping("/equipment-requests/{id}/review")
    public ApiResponse<EquipmentResponse> review(@PathVariable Integer id, @RequestBody ReviewRequest req,
                                                 @AuthenticationPrincipal AuthUser actor) {
        return ApiResponse.ok(equipmentService.review(id, req, actor == null ? null : actor.userId()));
    }

    /** Da lap dat xong: tao phu phi hang thang (body tuy chon). */
    @PostMapping("/equipment-requests/{id}/install")
    public ApiResponse<AddonFeeResponse> install(@PathVariable Integer id,
                                                 @RequestBody(required = false) InstallRequest req) {
        return ApiResponse.ok(equipmentService.install(id, req));
    }

    /** Da thao thiet bi: tat phu phi. */
    @PostMapping("/equipment-requests/{id}/remove")
    public ApiResponse<EquipmentResponse> remove(@PathVariable Integer id) {
        return ApiResponse.ok(equipmentService.remove(id));
    }

    @GetMapping("/room-addon-fees")
    public ApiResponse<List<AddonFeeResponse>> addonFees(@RequestParam(required = false) Integer roomId,
                                                         @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(equipmentService.addonFees(roomId, active));
    }

    // ----- UC52: hop dong sap het han -----

    @GetMapping("/contracts/expiring")
    public ApiResponse<List<ExpiringContract>> expiring(
            @RequestParam(defaultValue = "" + ContractExpiryService.DEFAULT_DAYS) int days) {
        return ApiResponse.ok(contractExpiryService.expiring(days));
    }

    /** Cap nhat ngay trang thai hop dong (job tu dong cung lam moi dem). */
    @PostMapping("/contracts/refresh-status")
    public ApiResponse<StatusRefreshResult> refreshStatus() {
        return ApiResponse.ok(contractExpiryService.refreshStatuses());
    }
}
