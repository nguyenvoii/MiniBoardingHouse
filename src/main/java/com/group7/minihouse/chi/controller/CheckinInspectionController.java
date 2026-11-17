package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.CheckinInspectionDTO;
import com.group7.minihouse.chi.service.CheckinInspectionService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/checkin-inspections")
public class CheckinInspectionController {

    private final CheckinInspectionService service;

    public CheckinInspectionController(CheckinInspectionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CheckinInspectionDTO>> getAll(@RequestParam(required = false) Integer roomId) {
        return ApiResponse.ok(service.getAll(roomId));
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckinInspectionDTO> getById(@PathVariable Integer id) {
        return ApiResponse.ok(service.getById(id));
    }

    @PostMapping
    public ApiResponse<CheckinInspectionDTO> create(@RequestBody CheckinInspectionDTO.CreateRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
