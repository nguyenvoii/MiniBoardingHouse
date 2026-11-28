package com.group7.minihouse.phi.controller;

import com.group7.minihouse.phi.dto.PaymentDtos.ConfirmResult;
import com.group7.minihouse.phi.dto.PaymentDtos.PaymentResponse;
import com.group7.minihouse.phi.service.PayOsClient;
import com.group7.minihouse.phi.service.PaymentService;
import com.group7.minihouse.shared.dto.ApiResponse;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.shared.security.CurrentUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "app.payment.payos.enabled", havingValue = "true")
@RequestMapping("/api/me/payments/{id}/payos")
public class PayOsCheckoutController {

    private final PaymentService payments;
    private final PayOsClient client;

    public PayOsCheckoutController(PaymentService payments, PayOsClient client) {
        this.payments = payments;
        this.client = client;
    }

    @PostMapping("/checkout")
    public ApiResponse<PayOsClient.Checkout> checkout(@PathVariable Integer id,
                                                       @AuthenticationPrincipal AuthUser actor) {
        var payment = payments.gatewayPaymentForTenant(id, CurrentUser.requireTenantId(actor));
        if (!"FAILED".equals(payment.status())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Giao dịch không còn chờ thanh toán");
        }
        return ApiResponse.ok(client.create(payments.nextGatewayOrder(id, CurrentUser.requireTenantId(actor)), amount(payment)));
    }

    @PostMapping("/reconcile")
    public ApiResponse<ConfirmResult> reconcile(@PathVariable Integer id, @AuthenticationPrincipal AuthUser actor) {
        Integer tenantId = CurrentUser.requireTenantId(actor);
        var payment = payments.gatewayPaymentForTenant(id, tenantId);
        if (!client.paid(payments.currentGatewayOrder(id, tenantId), amount(payment))) {
            return ApiResponse.ok(new ConfirmResult(false, "payOS chưa xác nhận đã thu đủ số tiền", payment));
        }
        return ApiResponse.ok(payments.reconcileGatewayPayment(id, tenantId, payment.amountPaid()));
    }

    private static long amount(PaymentResponse payment) {
        try {
            return payment.amountPaid().longValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Số tiền payOS phải là số nguyên VND");
        }
    }
}
