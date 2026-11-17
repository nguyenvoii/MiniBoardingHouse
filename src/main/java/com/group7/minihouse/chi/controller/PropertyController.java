package com.group7.minihouse.chi.controller;

import com.group7.minihouse.chi.dto.PropertyDTO;
import com.group7.minihouse.chi.service.PropertyService;
import com.group7.minihouse.shared.dto.ApiResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chi/properties")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER','METER_READER')")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @GetMapping
    public ApiResponse<List<PropertyDTO>> getAll() {
        return ApiResponse.ok(propertyService.getAll());
    }
}
