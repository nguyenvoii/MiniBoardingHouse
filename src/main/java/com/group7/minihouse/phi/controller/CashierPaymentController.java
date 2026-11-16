package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.PaymentDtos.CallbackRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmResult;
import com.group7.minihouse.phi.dto.PaymentDtos.MatchRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.MatchResult;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentRequest;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentResponse;
import com.group7.minihouse.phi.dto.PaymentDtos.QrInfo;
import com.group7.minihouse.phi.dto.PaymentDtos.QrRequest;
import com.group7.minihouse.phi.service.PaymentService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * UC85-88: thanh toan hoa don. Nhan vien thu ngan: LANDLORD, MANAGER, CASHIER. Cong thanh toan goi ve
 * /api/payments/callback kiem tra bi mat bang header X-Callback-Secret (app.payment.callback-secret, de trong = tat).
 */
@RestController
public class CashierPaymentController {

    private static final String CASH_STAFF = "hasAnyRole('LANDLORD','MANAGER','CASHIER')";

    private final PaymentService service;
    private final String callbackSecret;

    public CashierPaymentController(PaymentService service, @Value("${app.payment.callback-secret:}") String callbackSecret) {
        this.service = service;
        this.callbackSecret = callbackSecret == null ? "" : callbackSecret.trim();
    }

    @PostMapping("/api/payments")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<PaymentResponse> record(@RequestBody PaymentRequest req, @AuthenticationPrincipal AuthUser user,
                                               HttpServletRequest http) {
        return ApiResponse.ok(service.record(req, actor(user), http.getRemoteAddr()));
    }

    @PostMapping("/api/payments/qr")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<QrInfo> qr(@RequestBody QrRequest req, @AuthenticationPrincipal AuthUser user,
                                  HttpServletRequest http) {
        Integer id = actor(user);
        return ApiResponse.ok(service.createQr(req, id, id, http.getRemoteAddr()));
    }

    @PostMapping("/api/payments/match")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<MatchResult> match(@RequestBody MatchRequest req) {
        return ApiResponse.ok(service.match(req == null ? null : req.content()));
    }

    @PostMapping("/api/payments/{id}/confirm")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<ConfirmResult> confirm(@PathVariable Integer id, @RequestBody ConfirmRequest req,
                                              @AuthenticationPrincipal AuthUser user, HttpServletRequest http) {
        return ApiResponse.ok(service.confirm(id, req, actor(user), http.getRemoteAddr()));
    }

    @GetMapping("/api/payments")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<PageResponse<PaymentResponse>> search(@RequestParam(required = false) Integer invoiceId,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(invoiceId, keyword, status, page, size));
    }

    @GetMapping("/api/payments/{id}")
    @PreAuthorize(CASH_STAFF)
    public ApiResponse<PaymentResponse> get(@PathVariable Integer id) {
        return ApiResponse.ok(service.get(id));
    }

    /** Cong thanh toan goi ve (khong dang nhap, bao ve bang bi mat dung chung). */
    @PostMapping("/api/payments/callback")
    public ApiResponse<ConfirmResult> callback(@RequestBody CallbackRequest req,
                                               @RequestHeader(value = "X-Callback-Secret", required = false)
                                               String secret) {
        if (callbackSecret.isEmpty()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chua cau hinh app.payment.callback-secret");
        }
        boolean ok = secret != null && MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                callbackSecret.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Sai bi mat cong thanh toan");
        }
        return ApiResponse.ok(service.callback(req));
    }

    /** Nguoi thue tu lay ma QR de tra hoa don cua minh. */
    @PostMapping("/api/me/payments/qr")
    public ApiResponse<QrInfo> myQr(@RequestBody QrRequest req, @AuthenticationPrincipal AuthUser user,
                                    HttpServletRequest http) {
        return ApiResponse.ok(service.createQrForTenant(req, CurrentUser.requireTenantId(user),
                CurrentUser.requireUserId(user), http.getRemoteAddr()));
    }

    /** Ghi nhan thanh toan can biet ai nhan tien: khi tat bao mat (khong co nguoi dung) service bao ro. */
    private static Integer actor(AuthUser user) {
        return user == null ? null : user.userId();
    }
}
