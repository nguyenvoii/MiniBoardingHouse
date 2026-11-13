package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ChangePasswordRequest;
import com.group7.minihouse.phi.dto.ProfileResponse;
import com.group7.minihouse.phi.dto.UpdateProfileRequest;
import com.group7.minihouse.phi.service.AccountService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UC5, UC6: moi nguoi da dang nhap (nhan vien va nguoi thue) thao tac tren tai khoan cua chinh minh. */
@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        service.changePassword(CurrentUser.requireUserId(user), req.currentPassword(), req.newPassword(),
                http.getRemoteAddr());
        return ApiResponse.ok("Doi mat khau thanh cong", null);
    }

    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(service.getProfile(CurrentUser.requireUserId(user)));
    }

    @PutMapping("/profile")
    public ApiResponse<ProfileResponse> updateProfile(@RequestBody UpdateProfileRequest req,
                                                      @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.updateProfile(CurrentUser.requireUserId(user), req, http.getRemoteAddr()));
    }
}
