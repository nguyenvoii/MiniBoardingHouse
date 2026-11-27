package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.PaymentDtos.SepayWebhook;
import com.group7.minihouse.phi.service.SepayWebhookService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook SePay: tien chuyen khoan ve tai khoan thi SePay goi vao day. Khong dang nhap; bao ve bang header
 * "Authorization: Apikey {app.payment.sepay.api-key}" (cau hinh cung khoa nay o SePay, muc Webhooks, kieu chung thuc API Key).
 * Phan hoi co "success": true (ApiResponse) la SePay coi nhu da nhan.
 */
@RestController
public class SepayWebhookController {

    private final SepayWebhookService service;
    private final boolean enabled;
    private final byte[] expected;

    public SepayWebhookController(SepayWebhookService service,
                                  @Value("${app.payment.sepay.enabled:false}") boolean enabled,
                                  @Value("${app.payment.sepay.api-key:}") String apiKey) {
        this.service = service;
        this.enabled = enabled && apiKey != null && !apiKey.isBlank();
        this.expected = ("Apikey " + (apiKey == null ? "" : apiKey.trim())).getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/api/payments/sepay-webhook")
    public ApiResponse<Void> webhook(@RequestBody SepayWebhook body,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpServletRequest http) {
        if (!enabled) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chua bat SePay (app.payment.sepay.enabled va api-key)");
        }
        boolean ok = authorization != null
                && MessageDigest.isEqual(authorization.trim().getBytes(StandardCharsets.UTF_8), expected);
        if (!ok) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Sai khoa SePay");
        }
        return ApiResponse.ok(service.handle(body, http.getRemoteAddr()), null);
    }
}
