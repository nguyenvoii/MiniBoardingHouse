package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentRequest;
import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentResponse;
import com.group7.minihouse.phi.dto.OccupancyDtos.VehicleRequest;
import com.group7.minihouse.phi.dto.OccupancyDtos.VehicleResponse;
import com.group7.minihouse.phi.service.OccupancyService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xe cua khach thue va nguoi o cung trong phong: so luong that de tinh dich vu theo xe / theo dau nguoi.
 * Xem: moi nhan vien. Them/sua/xoa: LANDLORD, MANAGER.
 */
@RestController
public class OccupancyController {

    private static final String STAFF = "hasAnyRole('LANDLORD','MANAGER')";

    private final OccupancyService service;

    public OccupancyController(OccupancyService service) {
        this.service = service;
    }

    @GetMapping("/api/tenants/{tenantId}/vehicles")
    public ApiResponse<List<VehicleResponse>> vehicles(@PathVariable Integer tenantId) {
        return ApiResponse.ok(service.vehicles(tenantId));
    }

    @PostMapping("/api/tenants/{tenantId}/vehicles")
    @PreAuthorize(STAFF)
    public ApiResponse<VehicleResponse> addVehicle(@PathVariable Integer tenantId, @RequestBody VehicleRequest req,
                                                   @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.addVehicle(tenantId, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/vehicles/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<VehicleResponse> updateVehicle(@PathVariable Integer id, @RequestBody VehicleRequest req,
                                                      @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.updateVehicle(id, req, actor(user), http.getRemoteAddr()));
    }

    @DeleteMapping("/api/vehicles/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<Void> removeVehicle(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user,
                                           HttpServletRequest http) {
        service.removeVehicle(id, actor(user), http.getRemoteAddr());
        return ApiResponse.ok("Đã ngừng dùng xe", null);
    }

    @GetMapping("/api/rooms/{roomId}/residents")
    public ApiResponse<List<ResidentResponse>> residents(@PathVariable Integer roomId) {
        return ApiResponse.ok(service.residents(roomId));
    }

    @PostMapping("/api/rooms/{roomId}/residents")
    @PreAuthorize(STAFF)
    public ApiResponse<ResidentResponse> addResident(@PathVariable Integer roomId, @RequestBody ResidentRequest req,
                                                     @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.addResident(roomId, req, actor(user), http.getRemoteAddr()));
    }

    @PutMapping("/api/residents/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<ResidentResponse> updateResident(@PathVariable Integer id, @RequestBody ResidentRequest req,
                                                        @AuthenticationPrincipal AuthUser user,
                                                        HttpServletRequest http) {
        return ApiResponse.ok(service.updateResident(id, req, actor(user), http.getRemoteAddr()));
    }

    @DeleteMapping("/api/residents/{id}")
    @PreAuthorize(STAFF)
    public ApiResponse<Void> removeResident(@PathVariable Integer id, @AuthenticationPrincipal AuthUser user,
                                            HttpServletRequest http) {
        service.removeResident(id, actor(user), http.getRemoteAddr());
        return ApiResponse.ok("Đã bỏ người ở cùng", null);
    }

    /** Tat bao mat (dev) thi khong co nguoi dung: tra null, service bo qua nhat ky. */
    private static Integer actor(AuthUser user) {
        return user == null ? null : user.userId();
    }
}
