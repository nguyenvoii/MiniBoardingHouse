package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.TenantAccountResponse;
import com.group7.minihouse.phi.service.TenantAccountService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC10. Chi chu tro/quan ly (hasAnyRole chi co tac dung khi app.security.enabled=true). */
@RestController
@RequestMapping("/api/tenants")
public class TenantAccountController {

    private final TenantAccountService service;

    public TenantAccountController(TenantAccountService service) {
        this.service = service;
    }

    /** Cap tai khoan dang nhap cho khach thue; tra ve mat khau tam (chi hien 1 lan). */
    @PostMapping("/{tenantId}/account")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<TenantAccountResponse> createAccount(@PathVariable Integer tenantId,
                                                            @AuthenticationPrincipal AuthUser actor,
                                                            HttpServletRequest http) {
        return ApiResponse.ok(service.createAccount(tenantId, actor == null ? null : actor.userId(),
                http.getRemoteAddr()));
    }
}
