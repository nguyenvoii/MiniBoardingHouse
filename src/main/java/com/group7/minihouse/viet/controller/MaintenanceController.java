package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.AssignRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.CreateMaintenanceRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.InvoiceAdjustmentRequest;
import com.group7.minihouse.viet.dto.MaintenanceDtos.InvoiceAdjustmentResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.MaintenanceResponse;
import com.group7.minihouse.viet.dto.MaintenanceDtos.ResolveRequest;
import com.group7.minihouse.viet.service.MaintenanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC110-115: su co / sua chua (phia nhan vien). Chi LANDLORD, MANAGER. */
@RestController
@RequestMapping("/api/maintenance-requests")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    /** Nhan vien ghi nhan ho: bat buoc roomId, category (ma nhom su co), description. */
    @PostMapping
    public ApiResponse<MaintenanceResponse> create(@RequestBody CreateMaintenanceRequest req) {
        return ApiResponse.ok(service.create(req, null));
    }

    @GetMapping
    public ApiResponse<PageResponse<MaintenanceResponse>> search(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String priority,
                                                                 @RequestParam(required = false) String category,
                                                                 @RequestParam(required = false) Integer roomId,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(status, priority, category, roomId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaintenanceResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    /** UC114: phan cong nhan vien xu ly (OPEN -> ASSIGNED). */
    @PatchMapping("/{id}/assign")
    public ApiResponse<MaintenanceResponse> assign(@PathVariable Integer id, @RequestBody AssignRequest req) {
        return ApiResponse.ok(service.assign(id, req));
    }

    @PatchMapping("/{id}/start")
    public ApiResponse<MaintenanceResponse> start(@PathVariable Integer id) {
        return ApiResponse.ok(service.start(id));
    }

    /** UC114: nhap chi phi, ket qua xu ly (-> RESOLVED). */
    @PatchMapping("/{id}/resolve")
    public ApiResponse<MaintenanceResponse> resolve(@PathVariable Integer id,
                                                    @RequestBody(required = false) ResolveRequest req) {
        return ApiResponse.ok(service.resolve(id, req));
    }

    /** UC114: xac nhan hoan tat (RESOLVED -> CLOSED). */
    @PatchMapping("/{id}/close")
    public ApiResponse<MaintenanceResponse> close(@PathVariable Integer id) {
        return ApiResponse.ok(service.close(id));
    }

    /** UC111: giam tien hoa don sau khi kiem tra cong to/dong ho nuoc (su co phai RESOLVED hoac CLOSED). */
    @PostMapping("/{id}/invoice-adjustment")
    public ApiResponse<InvoiceAdjustmentResponse> adjustInvoice(@PathVariable Integer id,
                                                                @RequestBody InvoiceAdjustmentRequest req) {
        return ApiResponse.ok(service.adjustInvoice(id, req));
    }
}
