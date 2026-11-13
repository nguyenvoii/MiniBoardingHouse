package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.service.AuthService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Dang nhap bang email/SDT + mat khau, tra ve JWT. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, http.getRemoteAddr()));
    }

    /** Thong tin nguoi dang dang nhap (dung de thu token). */
    @GetMapping("/me")
    public ApiResponse<AuthUser> me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Chua dang nhap hoac token het han");
        }
        return ApiResponse.ok(user);
    }
}
