package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.service.QrLoginService;
import com.group7.minihouse.shared.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Ma QR dang nhap cho nguoi thue (xem QrLoginService). */
@RestController
public class QrLoginController {

    private final QrLoginService service;

    public QrLoginController(QrLoginService service) {
        this.service = service;
    }

    /** Nhan vien lay ma de hien QR cho khach vua duoc cap hoac cap lai mat khau tam. */
    @PostMapping("/api/tenants/{tenantId}/account/login-link")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<QrLoginService.Link> issue(@PathVariable Integer tenantId) {
        return ApiResponse.ok(service.issue(tenantId));
    }

    /** Cong khai: khach quet QR, trang web gui token len day de lay phien dang nhap. */
    @PostMapping("/api/auth/qr-login")
    public ApiResponse<LoginResponse> login(@RequestBody Map<String, String> body, HttpServletRequest http) {
        return ApiResponse.ok(service.login(body.get("token"), http.getRemoteAddr()));
    }
}
