package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.RoomAddonFeeDTO;
import com.group7.minihouse.chi.service.RoomAddonFeeService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/addon-fees")
@CrossOrigin(origins = "*")
public class RoomAddonFeeController {

    private final RoomAddonFeeService addonFeeService;

    public RoomAddonFeeController(RoomAddonFeeService addonFeeService) {
        this.addonFeeService = addonFeeService;
    }

    @GetMapping
    public ApiResponse<List<RoomAddonFeeDTO.Response>> getAll(
            @RequestParam(required = false) Integer roomId,
            @RequestParam(required = false) Boolean isActive) {
        return ApiResponse.ok(addonFeeService.getAll(roomId, isActive));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoomAddonFeeDTO.Response> getById(@PathVariable Integer id) {
        return ApiResponse.ok(addonFeeService.getById(id));
    }

    @PostMapping
    public ApiResponse<RoomAddonFeeDTO.Response> create(@RequestBody RoomAddonFeeDTO.CreateRequest req) {
        return ApiResponse.ok("Tạo phụ phí thành công", addonFeeService.create(req));
    }

    @PatchMapping("/{id}/toggle")
    public ApiResponse<RoomAddonFeeDTO.Response> toggleActive(@PathVariable Integer id) {
        return ApiResponse.ok("Đổi trạng thái phụ phí thành công", addonFeeService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        addonFeeService.delete(id);
        return ApiResponse.ok("Xóa phụ phí thành công", null);
    }
}
