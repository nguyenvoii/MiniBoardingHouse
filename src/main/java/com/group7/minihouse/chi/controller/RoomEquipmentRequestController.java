package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.RoomEquipmentRequestDTO;
import com.group7.minihouse.chi.service.RoomEquipmentRequestService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/equipment-requests")
@CrossOrigin(origins = "*")
public class RoomEquipmentRequestController {

    private final RoomEquipmentRequestService requestService;

    public RoomEquipmentRequestController(RoomEquipmentRequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping
    public ApiResponse<List<RoomEquipmentRequestDTO.Response>> getAll(
            @RequestParam(required = false) Integer roomId,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(requestService.getAll(roomId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomEquipmentRequestDTO.Response> getById(@PathVariable Integer id) {
        return ApiResponse.ok(requestService.getById(id));
    }

    @PostMapping
    public ApiResponse<RoomEquipmentRequestDTO.Response> create(@RequestBody RoomEquipmentRequestDTO.CreateRequest req) {
        return ApiResponse.ok("Tạo yêu cầu thiết bị thành công", requestService.create(req));
    }

    @PatchMapping("/{id}/approve")
    public ApiResponse<RoomEquipmentRequestDTO.Response> approve(@PathVariable Integer id) {
        return ApiResponse.ok("Duyệt yêu cầu thành công", requestService.approve(id));
    }

    @PatchMapping("/{id}/reject")
    public ApiResponse<RoomEquipmentRequestDTO.Response> reject(
            @PathVariable Integer id,
            @RequestBody(required = false) RoomEquipmentRequestDTO.RejectRequest req) {
        String reason = (req != null) ? req.getReason() : null;
        return ApiResponse.ok("Đã từ chối yêu cầu", requestService.reject(id, reason));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<RoomEquipmentRequestDTO.Response> updateStatus(
            @PathVariable Integer id,
            @RequestParam String status) {
        return ApiResponse.ok("Cập nhật trạng thái thành công", requestService.updateStatus(id, status));
    }
}
