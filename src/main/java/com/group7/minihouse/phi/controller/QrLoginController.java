package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.service.QrLoginService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final JwtService jwtService;

    public QrLoginController(QrLoginService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    /** Nhan vien lay ma de hien QR cho khach vua duoc cap hoac cap lai mat khau tam. */
    @PostMapping("/api/tenants/{tenantId}/account/login-link")
    @PreAuthorize("hasAnyRole('LANDLORD','MANAGER')")
    public ApiResponse<QrLoginService.Link> issue(@PathVariable Integer tenantId) {
        return ApiResponse.ok(service.issue(tenantId));
    }

    /** Phien dang nhap bang QR dat mat khau moi, khong can mat khau tam. Phien dang nhap thuong phai dung /api/auth/password. */
    @PostMapping("/api/auth/qr-set-password")
    public ApiResponse<Void> setPassword(@RequestBody Map<String, String> body, @AuthenticationPrincipal AuthUser user,
                                         jakarta.servlet.http.HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || !jwtService.isQrSession(header.substring(7).trim())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ phiên đăng nhập bằng mã QR mới dùng được chức năng này");
        }
        service.setFirstPassword(CurrentUser.requireUserId(user), body.get("newPassword"), http.getRemoteAddr());
        return ApiResponse.ok("Đã đặt mật khẩu mới", null);
    }

    /** Cong khai: khach quet QR, trang web gui token len day de lay phien dang nhap. */
    @PostMapping("/api/auth/qr-login")
    public ApiResponse<LoginResponse> login(@RequestBody Map<String, String> body, HttpServletRequest http) {
        return ApiResponse.ok(service.login(body.get("token"), http.getRemoteAddr()));
    }
}
