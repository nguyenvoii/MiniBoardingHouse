package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.ForgotPasswordRequest;
import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.dto.RegisterRequest;
import com.group7.minihouse.phi.dto.ResetPasswordRequest;
import com.group7.minihouse.phi.dto.VerifyEmailRequest;
import com.group7.minihouse.phi.service.RegistrationService;
import com.group7.minihouse.phi.service.ActivityLogService;
import com.group7.minihouse.phi.service.AuthService;
import com.group7.minihouse.phi.service.PasswordRecoveryService;
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
    private final ActivityLogService activityLogService;
    private final PasswordRecoveryService recoveryService;
    private final RegistrationService registrationService;

    public AuthController(AuthService authService, ActivityLogService activityLogService,
                          PasswordRecoveryService recoveryService, RegistrationService registrationService) {
        this.registrationService = registrationService;
        this.authService = authService;
        this.activityLogService = activityLogService;
        this.recoveryService = recoveryService;
    }

    /** Dang nhap bang email/SDT + mat khau, tra ve JWT. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(req, http.getRemoteAddr()));
    }

    /** Dang xuat: JWT khong luu phien nen client chi viec bo token; endpoint nay ghi nhat ky LOGOUT. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        if (user != null) {
            activityLogService.log(user.userId(), "LOGOUT", "Dang xuat", http.getRemoteAddr());
        }
        return ApiResponse.ok(null);
    }

    /** UC1: dang ky chu tro. Tai khoan o trang thai INACTIVE cho den khi xac thuc email (UC2). */
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest req, HttpServletRequest http) {
        registrationService.register(req, http.getRemoteAddr());
        return ApiResponse.ok("Da gui ma xac thuc ve email, hay nhap ma de kich hoat tai khoan", null);
    }

    /** UC2: nhap ma OTP gui qua email de kich hoat tai khoan. */
    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest req, HttpServletRequest http) {
        registrationService.verifyEmail(req.email(), req.code(), http.getRemoteAddr());
        return ApiResponse.ok("Da kich hoat tai khoan, hay dang nhap", null);
    }

    /** UC2: gui lai ma xac thuc. Luon tra cung 1 thong bao. */
    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ForgotPasswordRequest req) {
        registrationService.resendVerification(req.identifier());
        return ApiResponse.ok("Neu tai khoan dang cho xac thuc, ma moi da duoc gui", null);
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
