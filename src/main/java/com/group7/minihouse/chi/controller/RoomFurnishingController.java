package com.group7.minihouse.chi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.chi.dto.RoomFurnishingDTO;
import com.group7.minihouse.chi.service.RoomFurnishingService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/furnishings")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomFurnishingController {

    private final RoomFurnishingService furnishingService;

    public RoomFurnishingController(RoomFurnishingService furnishingService) {
        this.furnishingService = furnishingService;
    }

    @GetMapping
    public ApiResponse<List<RoomFurnishingDTO>> getAllFurnishings() {
        return ApiResponse.ok(furnishingService.getAllFurnishings());
    }

    @GetMapping("/room/{roomId}")
    public ApiResponse<List<RoomFurnishingDTO>> getFurnishingsByRoom(@PathVariable Integer roomId) {
        return ApiResponse.ok(furnishingService.getFurnishingsByRoom(roomId));
    }

    // UC 24: Lấy danh mục thiết bị, nội thất chuẩn
    @GetMapping("/standard-catalog")
    public ApiResponse<List<RoomFurnishingDTO>> getStandardCatalog() {
        return ApiResponse.ok(furnishingService.getStandardCatalog());
    }

    // UC 24: Áp dụng bộ nội thất chuẩn cho phòng
    @PostMapping("/apply-standard/{roomId}")
    public ApiResponse<List<RoomFurnishingDTO>> applyStandard(@PathVariable Integer roomId) {
        List<RoomFurnishingDTO> added = furnishingService.applyStandardFurnishings(roomId);
        return ApiResponse.ok("Đã áp dụng bộ nội thất chuẩn cho phòng thành công (" + added.size() + " thiết bị mới)", added);
    }

    @PostMapping
    public ApiResponse<RoomFurnishingDTO> addFurnishing(@RequestBody RoomFurnishingDTO dto) {
        return ApiResponse.ok("Thêm thiết bị thành công", furnishingService.addFurnishing(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoomFurnishingDTO> updateFurnishing(@PathVariable Integer id, @RequestBody RoomFurnishingDTO dto) {
        return ApiResponse.ok("Cập nhật thiết bị thành công", furnishingService.updateFurnishing(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFurnishing(@PathVariable Integer id) {
        furnishingService.deleteFurnishing(id);
        return ApiResponse.ok("Xóa thiết bị thành công", null);
    }
}
