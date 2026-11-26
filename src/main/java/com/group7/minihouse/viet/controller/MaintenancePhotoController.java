package com.group7.minihouse.viet.controller;

import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.viet.service.MaintenanceService;
import org.springframework.core.io.PathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaintenancePhotoController {

    private final MaintenanceService service;

    public MaintenancePhotoController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping(MaintenanceService.PHOTO_ROUTE + "/{name}")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER','TENANT')")
    public ResponseEntity<PathResource> photo(@PathVariable String name, @AuthenticationPrincipal AuthUser user) {
        CurrentUser.requireUserId(user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FileStorage.contentTypeOf(name)))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new PathResource(service.photo(name, user.tenantId())));
    }
}
