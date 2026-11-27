package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.BackupInfo;
import com.group7.minihouse.phi.dto.RestoreRequest;
import com.group7.minihouse.phi.service.BackupService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);
    private final BackupService service;
    private final boolean enabled;

    public BackupController(BackupService service, @Value("${app.backup.enabled:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @GetMapping("/availability")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Boolean> availability() {
        return ApiResponse.ok(enabled);
    }

    @GetMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<List<BackupInfo>> list() {
        requireEnabled();
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<BackupInfo> backup(@AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        requireEnabled();
        return ApiResponse.ok(service.backup(CurrentUser.requireUserId(user), http.getRemoteAddr()));
    }

    /** Ghi de toan bo du lieu bang ban sao luu. Body {"confirm":"<ten co so du lieu>"}. */
    @PostMapping("/{name}/restore")
    @PreAuthorize("hasRole('LANDLORD')")
    public ApiResponse<Void> restore(@PathVariable String name, @RequestBody RestoreRequest req,
                                     @AuthenticationPrincipal AuthUser user) {
        requireEnabled();
        Integer actorId = CurrentUser.requireUserId(user);
        log.warn("Database restore requested by user {}", actorId);
        service.restore(name, req.confirm());
        log.warn("Database restore completed for user {}", actorId);
        return ApiResponse.ok("Da phuc hoi du lieu. Hay dang nhap lai.", null);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw BusinessException.notFound("Sao luu va phuc hoi trong ung dung");
        }
    }
}
