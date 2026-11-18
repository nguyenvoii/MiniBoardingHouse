package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.PropertyFloorDTO;
import com.group7.minihouse.chi.service.PropertyFloorService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UC 15: Thêm, sửa, xóa tầng.
 */
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
@RestController
@RequestMapping("/api/chi/floors")
public class PropertyFloorController {

    private final PropertyFloorService floorService;

    public PropertyFloorController(PropertyFloorService floorService) {
        this.floorService = floorService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PropertyFloorDTO.Response>>> getFloors(
            @RequestParam(required = false) Integer propertyId) {
        return ResponseEntity.ok(ApiResponse.ok(floorService.getFloors(propertyId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PropertyFloorDTO.Response>> createFloor(
            @RequestBody PropertyFloorDTO.CreateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Thêm tầng thành công (UC 15)", floorService.createFloor(req)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PropertyFloorDTO.Response>> updateFloor(
            @PathVariable Integer id,
            @RequestBody PropertyFloorDTO.UpdateRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật tầng thành công (UC 15)", floorService.updateFloor(id, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFloor(@PathVariable Integer id) {
        floorService.deleteFloor(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa tầng thành công (UC 15)", null));
    }
}
