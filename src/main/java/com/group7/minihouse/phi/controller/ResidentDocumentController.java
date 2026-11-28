package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.OccupancyDtos.ResidentResponse;
import com.group7.minihouse.phi.service.ResidentDocumentService;
import com.group7.minihouse.phi.service.ResidentDocumentService.TempResidenceDue;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Anh CCCD cua nguoi o cung va nhac khai bao tam tru. Chi chu tro / quan ly (giay to nhay cam). */
@RestController
@PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
public class ResidentDocumentController {

    private final ResidentDocumentService service;

    public ResidentDocumentController(ResidentDocumentService service) {
        this.service = service;
    }

    @PostMapping("/api/residents/{id}/id-card")
    public ApiResponse<ResidentResponse> upload(@PathVariable Integer id,
                                                @RequestParam(value = "front", required = false) MultipartFile front,
                                                @RequestParam(value = "back", required = false) MultipartFile back,
                                                @AuthenticationPrincipal AuthUser user, HttpServletRequest http)
            throws IOException {
        var r = service.uploadIdCard(id, front == null ? null : front.getBytes(), back == null ? null : back.getBytes(),
                user == null ? null : user.userId(), http.getRemoteAddr());
        return ApiResponse.ok("Đã lưu ảnh căn cước", new ResidentResponse(r.getResidentId(), r.getRoom().getRoomId(),
                r.getFullName(), r.getPhoneNumber(), r.getIdentityNumber(), r.getRelationshipWithPrimary(),
                r.getStartDate(), r.getDateOfBirth(), r.getPermanentAddress(), r.getTempResidenceRegisteredOn(),
                r.getIdFrontImageUrl(), r.getIdBackImageUrl(), null));
    }

    @GetMapping("/api/residents/{id}/id-card/photos/{name}")
    public ResponseEntity<PathResource> photo(@PathVariable Integer id, @PathVariable String name) {
        return service.photo(id, name)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                        .cacheControl(CacheControl.noStore())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(new PathResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/residents/temp-residence-due")
    public ApiResponse<List<TempResidenceDue>> due(@RequestParam(value = "propertyId", required = false) Integer propertyId) {
        return ApiResponse.ok(service.tempResidenceDue(propertyId));
    }
}
