package com.group7.minihouse.chi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.service.RoomService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/chi/rooms")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER','METER_READER')")
    @GetMapping
    public ApiResponse<List<RoomDTO>> getAllRooms(@RequestParam(value = "propertyId", required = false) Integer propertyId) {
        return ApiResponse.ok(roomService.getAllRooms(propertyId));
    }

    // UC 19 & UC 20: Tìm kiếm theo mã phòng & lọc theo diện tích, giá
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER','METER_READER')")
    @GetMapping("/search")
    public ApiResponse<List<RoomDTO>> searchRooms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer floorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minArea,
            @RequestParam(required = false) BigDecimal maxArea,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice) {
        return ApiResponse.ok(roomService.searchRooms(keyword, floorId, status, minArea, maxArea, minPrice, maxPrice));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDTO> getRoomById(@PathVariable Integer id) {
        return ApiResponse.ok(roomService.getRoomById(id));
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER','METER_READER')")
    @GetMapping("/floor/{floorId}")
    public ApiResponse<List<RoomDTO>> getRoomsByFloor(@PathVariable Integer floorId) {
        return ApiResponse.ok(roomService.getRoomsByFloor(floorId));
    }

    @PostMapping
    public ApiResponse<RoomDTO> createRoom(@RequestBody RoomDTO dto) {
        return ApiResponse.ok("Tạo phòng thành công", roomService.createRoom(dto));
    }

    // UC 18 & UC 27: Sửa phòng (mã, diện tích, giá, cọc, chỉ số công tơ)
    @PutMapping("/{id}")
    public ApiResponse<RoomDTO> updateRoom(@PathVariable Integer id, @RequestBody RoomDTO dto) {
        return ApiResponse.ok("Cập nhật phòng thành công", roomService.updateRoom(id, dto));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RoomDTO> updateStatus(@PathVariable Integer id, @RequestParam String status) {
        return ApiResponse.ok("Cập nhật trạng thái thành công", roomService.updateRoomStatus(id, status));
    }

    // UC 29: Xác nhận phòng đã vệ sinh xong, cho thuê lại (REPAIRING -> VACANT)
    @PatchMapping("/{id}/confirm-cleaned")
    public ApiResponse<RoomDTO> confirmCleaned(@PathVariable Integer id) {
        return ApiResponse.ok("Đã xác nhận vệ sinh phòng xong, sẵn sàng cho thuê", roomService.confirmCleanedAndAvailable(id));
    }
}
