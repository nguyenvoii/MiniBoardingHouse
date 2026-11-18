package com.group7.minihouse.chi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.chi.dto.ResidentStayHistoryDTO;
import com.group7.minihouse.chi.dto.RoomResidentDTO;
import com.group7.minihouse.chi.dto.TenantDTO;
import com.group7.minihouse.chi.dto.TenantVehicleDTO;
import com.group7.minihouse.chi.service.TenantService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chi/tenants")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping
    public ApiResponse<List<TenantDTO>> getAllTenants(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(tenantService.getAllTenants(keyword));
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/{id}")
    public ApiResponse<TenantDTO> getById(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getById(id));
    }

    @PostMapping
    public ApiResponse<TenantDTO> createTenant(@RequestBody TenantDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.createTenant(req));
    }

    // UC 31: Sửa hồ sơ khách thuê, ngày cư trú
    @PutMapping("/{id}")
    public ApiResponse<TenantDTO> updateTenant(@PathVariable Integer id, @RequestBody TenantDTO.CreateRequest req) {
        return ApiResponse.ok("Cập nhật hồ sơ khách thuê thành công", tenantService.updateTenant(id, req));
    }

    // UC 35: Lịch sử thuê phòng của khách
    @GetMapping("/{id}/stay-history")
    public ApiResponse<List<ResidentStayHistoryDTO>> getStayHistory(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getStayHistory(id));
    }

    @PatchMapping("/{id}/verify")
    public ApiResponse<Void> verifyTenant(@PathVariable Integer id) {
        tenantService.verifyTenant(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/id-card")
    public ApiResponse<Void> saveIdCard(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        tenantService.saveIdCard(id, body.get("frontUrl"), body.get("backUrl"));
        return ApiResponse.ok(null);
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/{id}/vehicles")
    public ApiResponse<List<TenantVehicleDTO>> getVehicles(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getVehiclesByTenant(id));
    }

    @PostMapping("/vehicles")
    public ApiResponse<TenantVehicleDTO> addVehicle(@RequestBody TenantVehicleDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.addVehicle(req));
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/residents/room/{roomId}")
    public ApiResponse<List<RoomResidentDTO>> getResidentsByRoom(@PathVariable Integer roomId) {
        return ApiResponse.ok(tenantService.getResidentsByRoom(roomId));
    }

    @PostMapping("/residents")
    public ApiResponse<RoomResidentDTO> addResident(@RequestBody RoomResidentDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.addResident(req));
    }

    @DeleteMapping("/residents/{id}")
    public ApiResponse<Void> removeResident(@PathVariable Integer id) {
        tenantService.removeResident(id);
        return ApiResponse.ok(null);
    }
}
