package com.group7.minihouse.chi.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import com.group7.minihouse.chi.dto.ResidentStayHistoryDTO;
import com.group7.minihouse.chi.dto.RoomResidentDTO;
import com.group7.minihouse.chi.dto.TenantDTO;
import com.group7.minihouse.chi.dto.TenantVehicleDTO;
import com.group7.minihouse.chi.service.TenantService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.shared.exception.BusinessException;
import java.io.IOException;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chi/tenants")
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class TenantController {

    private static final String CARD_FOLDER = "tenant-id-cards";
    private static final String CARD_PHOTO_ROUTE = "/id-card/photos";

    private final TenantService tenantService;
    private final FileStorage storage;

    public TenantController(TenantService tenantService, FileStorage storage) {
        this.tenantService = tenantService;
        this.storage = storage;
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping
    public ApiResponse<List<TenantDTO>> getAllTenants(@RequestParam(value = "keyword", required = false) String keyword,
                                                      @RequestParam(value = "limit", required = false) Integer limit,
                                                      @RequestParam(value = "available", defaultValue = "false") boolean available,
                                                      @RequestParam(value = "renting", defaultValue = "false") boolean renting) {
        if (limit != null || available || renting) {
            return ApiResponse.ok(tenantService.search(keyword, limit, available, renting));
        }
        return ApiResponse.ok(tenantService.getAllTenants(keyword));
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/{id}")
    public ApiResponse<TenantDTO> getById(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getById(id));
    }

    @PostMapping
    public ApiResponse<TenantDTO> createTenant(@RequestBody TenantDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.createTenant(req));
    }

    // UC 31: Sửa hồ sơ khách thuê, ngày cư trú
    @PutMapping("/{id}")
    public ApiResponse<TenantDTO> updateTenant(@PathVariable Integer id, @RequestBody TenantDTO.CreateRequest req) {
        return ApiResponse.ok("Cập nhật hồ sơ khách thuê thành công", tenantService.updateTenant(id, req));
    }

    // UC 35: Lịch sử thuê phòng của khách
    @GetMapping("/{id}/stay-history")
    public ApiResponse<List<ResidentStayHistoryDTO>> getStayHistory(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getStayHistory(id));
    }

    @PatchMapping("/{id}/verify")
    public ApiResponse<Void> verifyTenant(@PathVariable Integer id) {
        tenantService.verifyTenant(id);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{id}/id-card", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> saveIdCard(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String front = body.getOrDefault("frontUrl", "");
        String back = body.getOrDefault("backUrl", "");
        if ((front != null && front.startsWith("/api/chi/tenants/"))
                || (back != null && back.startsWith("/api/chi/tenants/"))) {
            throw new BusinessException("Hãy tải tệp ảnh căn cước qua multipart");
        }
        tenantService.saveIdCard(id, front, back);
        return ApiResponse.ok(null);
    }

    @PostMapping(value = "/{id}/id-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> uploadIdCard(@PathVariable Integer id, @RequestParam("front") MultipartFile front,
                                           @RequestParam("back") MultipartFile back) throws IOException {
        byte[] frontBytes = front.getBytes();
        byte[] backBytes = back.getBytes();
        long maxSize = 2L * 1024 * 1024;
        String frontName = storage.saveImage(CARD_FOLDER, frontBytes, maxSize);
        String backName = null;
        try {
            backName = storage.saveImage(CARD_FOLDER, backBytes, maxSize);
            String baseUrl = "/api/chi/tenants/" + id + CARD_PHOTO_ROUTE + "/";
            var previous = tenantService.getById(id);
            tenantService.saveIdCard(id, baseUrl + frontName, baseUrl + backName);
            for (String oldUrl : new String[] {previous.getFrontImageUrl(), previous.getBackImageUrl()}) {
                if (oldUrl != null && oldUrl.startsWith(baseUrl)) { // anh cu khong con duoc dung: xoa de khong ton du lieu nhay cam
                    try {
                        storage.delete(CARD_FOLDER, oldUrl.substring(baseUrl.length()));
                    } catch (RuntimeException ignore) {
                        // khong de loi don dep lam hong viec luu anh moi
                    }
                }
            }
        } catch (RuntimeException failure) {
            discardUpload(frontName, failure);
            discardUpload(backName, failure);
            throw failure;
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}" + CARD_PHOTO_ROUTE + "/{name}")
    public ResponseEntity<PathResource> idCardPhoto(@PathVariable Integer id, @PathVariable String name) {
        String url = "/api/chi/tenants/" + id + CARD_PHOTO_ROUTE + "/" + name;
        if (!tenantService.hasIdCardPhoto(id, url)) {
            return ResponseEntity.notFound().build();
        }
        return storage.find(CARD_FOLDER, name)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .cacheControl(CacheControl.noStore())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(new PathResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void discardUpload(String name, RuntimeException failure) {
        try {
            storage.delete(CARD_FOLDER, name);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/{id}/vehicles")
    public ApiResponse<List<TenantVehicleDTO>> getVehicles(@PathVariable Integer id) {
        return ApiResponse.ok(tenantService.getVehiclesByTenant(id));
    }

    @PostMapping("/vehicles")
    public ApiResponse<TenantVehicleDTO> addVehicle(@RequestBody TenantVehicleDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.addVehicle(req));
    }

    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','CASHIER')")
    @GetMapping("/residents/room/{roomId}")
    public ApiResponse<List<RoomResidentDTO>> getResidentsByRoom(@PathVariable Integer roomId) {
        return ApiResponse.ok(tenantService.getResidentsByRoom(roomId));
    }

    @PostMapping("/residents")
    public ApiResponse<RoomResidentDTO> addResident(@RequestBody RoomResidentDTO.CreateRequest req) {
        return ApiResponse.ok(tenantService.addResident(req));
    }

    @DeleteMapping("/residents/{id}")
    public ApiResponse<Void> removeResident(@PathVariable Integer id) {
        tenantService.removeResident(id);
        return ApiResponse.ok(null);
    }
}
