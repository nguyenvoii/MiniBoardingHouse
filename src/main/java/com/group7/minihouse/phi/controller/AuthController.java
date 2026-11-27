package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ForgotPasswordRequest;
import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.dto.ResetPasswordRequest;
import com.group7.minihouse.phi.dto.VerifyPhoneRequest;
import com.group7.minihouse.phi.service.RegistrationService;
import com.group7.minihouse.phi.service.AuthService;
import com.group7.minihouse.phi.service.PasswordRecoveryService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import com.group7.minihouse.shared.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService recoveryService;
    private final RegistrationService registrationService;

    public AuthController(AuthService authService, PasswordRecoveryService recoveryService,
                          RegistrationService registrationService) {
        this.registrationService = registrationService;
        this.authService = authService;
        this.recoveryService = recoveryService;
    }

    /** Dang nhap bang email/SDT + mat khau, tra ve JWT. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, http.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication, HttpServletRequest http) {
        if (authentication != null && authentication.getDetails() instanceof JwtService.Session session) {
            authService.logout(session, http.getRemoteAddr());
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/send-phone-verification")
    public ApiResponse<Void> sendPhoneVerification(@Valid @RequestBody ForgotPasswordRequest req,
                                                    @AuthenticationPrincipal AuthUser actor) {
        registrationService.requestPhoneVerification(CurrentUser.requireUserId(actor), req.identifier());
        return ApiResponse.ok("Da gui ma xac thuc so dien thoai", null);
    }

    @PostMapping("/verify-phone")
    public ApiResponse<Void> verifyPhone(@Valid @RequestBody VerifyPhoneRequest req, @AuthenticationPrincipal AuthUser actor,
                                         HttpServletRequest http) {
        registrationService.verifyPhone(CurrentUser.requireUserId(actor), req.phone(), req.code(), http.getRemoteAddr());
        return ApiResponse.ok("Da xac thuc so dien thoai", null);
    }

    /** UC4 buoc 1: xin ma OTP dat lai mat khau. Luon tra cung 1 thong bao du tai khoan co ton tai hay khong. */
    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        recoveryService.forgotPassword(req.identifier(), http.getRemoteAddr());
        return ApiResponse.ok("Neu tai khoan ton tai, ma xac thuc da duoc gui", null);
    }

    /** UC4 buoc 2: nhap ma OTP + mat khau moi. */
    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        recoveryService.resetPassword(req.identifier(), req.code(), req.newPassword(), http.getRemoteAddr());
        return ApiResponse.ok("Da dat lai mat khau, hay dang nhap bang mat khau moi", null);
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
