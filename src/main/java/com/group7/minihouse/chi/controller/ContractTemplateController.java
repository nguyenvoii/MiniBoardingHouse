package com.group7.minihouse.chi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.chi.dto.ContractTemplateDTO;
import com.group7.minihouse.chi.service.ContractTemplateService;
import com.group7.minihouse.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chi/contract-templates")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    public ContractTemplateController(ContractTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<ContractTemplateDTO.Response>> getAll() {
        return ApiResponse.ok(templateService.getAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractTemplateDTO.Response> getById(@PathVariable Integer id) {
        return ApiResponse.ok(templateService.getById(id));
    }

    @PostMapping
    public ApiResponse<ContractTemplateDTO.Response> create(@RequestBody ContractTemplateDTO.Request req) {
        return ApiResponse.ok("Tạo mẫu hợp đồng thành công", templateService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ContractTemplateDTO.Response> update(@PathVariable Integer id,
                                                            @RequestBody ContractTemplateDTO.Request req) {
        return ApiResponse.ok("Cập nhật mẫu hợp đồng thành công", templateService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        templateService.delete(id);
        return ApiResponse.ok("Đã xóa mẫu hợp đồng", null);
    }
}
