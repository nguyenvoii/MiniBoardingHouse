package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.PermissionResponse;
import com.group7.minihouse.phi.repository.PermissionRepository;
import com.group7.minihouse.phi.service.RolePermissions;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.entity.Permission;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC11, UC12: xem quyen chi tiet. Chi la bang tra cuu cho giao dien (hien/an chuc nang);
 * viec chan API that su la @PreAuthorize theo vai tro (xem RolePermissions).
 */
@RestController
public class PermissionController {

    private final PermissionRepository repository;

    public PermissionController(PermissionRepository repository) {
        this.repository = repository;
    }

    /** Toan bo quyen he thong, xep theo phan he. */
    @GetMapping("/api/permissions")
    @Transactional(readOnly = true)
    public ApiResponse<List<PermissionResponse>> catalog() {
        return ApiResponse.ok(repository.findAllByOrderByModuleAscPermissionCodeAsc().stream()
                .map(p -> new PermissionResponse(p.getPermissionCode(), p.getPermissionName(), p.getModule()))
                .toList());
    }

    /** Quyen mac dinh cua tung vai tro (LANDLORD, MANAGER xem). */
    @GetMapping("/api/roles/permissions")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Set<String>>> roleMatrix() {
        List<String> codes = allCodes();
        Map<String, Set<String>> matrix = new LinkedHashMap<>();
        for (String role : RolePermissions.ROLES) {
            matrix.put(role, RolePermissions.defaults(role, codes));
        }
        return ApiResponse.ok(matrix);
    }

    /** Quyen cua chinh nguoi dang nhap (hop quyen cac vai tro dang giu; nguoi thue tra ve rong). */
    @GetMapping("/api/auth/permissions")
    @Transactional(readOnly = true)
    public ApiResponse<Set<String>> mine(@AuthenticationPrincipal AuthUser user) {
        AuthUser u = user;
        CurrentUser.requireUserId(u);
        return ApiResponse.ok(RolePermissions.effective(u.roles(), allCodes()));
    }

    private List<String> allCodes() {
        return repository.findAllByOrderByModuleAscPermissionCodeAsc().stream()
                .map(Permission::getPermissionCode).toList();
    }
}
