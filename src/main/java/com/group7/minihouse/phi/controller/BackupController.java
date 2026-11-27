package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.BackupInfo;
import com.group7.minihouse.phi.dto.RestoreRequest;
import com.group7.minihouse.phi.service.BackupService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC122: sao luu / phuc hoi CSDL. Chi LANDLORD (khi app.security.enabled=true). */
@RestController
@RequestMapping("/api/admin/backups")
public class BackupController {

    private final BackupService service;

    public BackupController(BackupService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<List<BackupInfo>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<BackupInfo> backup(@AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.backup(CurrentUser.requireUserId(user), http.getRemoteAddr()));
    }

    /** Ghi de toan bo du lieu bang ban sao luu. Body {"confirm":"<ten co so du lieu>"}. */
    @PostMapping("/{name}/restore")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> restore(@PathVariable String name, @RequestBody RestoreRequest req) {
        service.restore(name, req.confirm());
        return ApiResponse.ok("Da phuc hoi du lieu. Hay dang nhap lai.", null);
    }
}
