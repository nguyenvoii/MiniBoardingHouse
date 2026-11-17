package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.RoomDTO;
import com.group7.minihouse.chi.service.RoomService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public ApiResponse<List<RoomDTO>> getAllRooms() {
        return ApiResponse.ok(roomService.getAllRooms());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomDTO> getRoomById(@PathVariable Integer id) {
        return ApiResponse.ok(roomService.getRoomById(id));
    }

    @GetMapping("/floor/{floorId}")
    public ApiResponse<List<RoomDTO>> getRoomsByFloor(@PathVariable Integer floorId) {
        return ApiResponse.ok(roomService.getRoomsByFloor(floorId));
    }

    @PostMapping
    public ApiResponse<RoomDTO> createRoom(@RequestBody RoomDTO dto) {
        return ApiResponse.ok("Tạo phòng thành công", roomService.createRoom(dto));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RoomDTO> updateStatus(@PathVariable Integer id, @RequestParam String status) {
        return ApiResponse.ok("Cập nhật trạng thái thành công", roomService.updateRoomStatus(id, status));
    }
}
