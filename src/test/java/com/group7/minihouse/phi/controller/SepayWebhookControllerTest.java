package com.group7.minihouse.phi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.PaymentDtos.SepayWebhook;
import com.group7.minihouse.phi.service.PaymentService;
import com.group7.minihouse.phi.service.SepayWebhookService;
import com.group7.minihouse.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Webhook SePay: kiem khoa, loc giao dich, nuot loi nghiep vu (khong can DB). */
class SepayWebhookControllerTest {

    static final String KEY = "khoa-sepay-thu-nghiem-0123456789abcdef";
    final PaymentService payments = mock(PaymentService.class);
    final SepayWebhookService service = new SepayWebhookService(payments);
    final HttpServletRequest http = mock(HttpServletRequest.class);

    private SepayWebhook in(long id, String amount) {
        return new SepayWebhook(id, "MBBank", "2026-11-27 17:00:00", "0071000888888", null,
                "CK HD202611P101", "in", new BigDecimal(amount), "FT1", "desc");
    }

    @Test
    void rejectsMissingOrWrongKeyAndNeverTouchesPayments() {
        var c = new SepayWebhookController(service, true, KEY);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> c.webhook(in(1, "1000"), null, http)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> c.webhook(in(1, "1000"), "Apikey sai-khoa", http)).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(BusinessException.class,
                () -> c.webhook(in(1, "1000"), "Bearer " + KEY, http)).getStatus());
        verify(payments, never()).confirmSepayTransfer(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
    }

    @Test
    void disabledOrBlankKeyIsClosedEvenWithTheRightHeader() {
        assertThrows(BusinessException.class, () -> new SepayWebhookController(service, false, KEY)
                .webhook(in(1, "1000"), "Apikey " + KEY, http));
        assertThrows(BusinessException.class, () -> new SepayWebhookController(service, true, "  ")
                .webhook(in(1, "1000"), "Apikey ", http));
    }

    @Test
    void rightKeyForwardsMoneyInAndReturnsSuccess() {
        when(http.getRemoteAddr()).thenReturn("9.9.9.9");
        when(payments.confirmSepayTransfer(eq(5L), anyString(), eq(new BigDecimal("3000000")), eq("9.9.9.9")))
                .thenReturn("Da cong");
        var r = new SepayWebhookController(service, true, KEY).webhook(in(5, "3000000"), "Apikey " + KEY, http);
        assertEquals(true, r.isSuccess());
        assertEquals("Da cong", r.getMessage());
    }

    @Test
    void moneyOutAndBadPayloadAreIgnoredWithoutError() {
        var out = new SepayWebhook(6L, "MBBank", null, null, null, "HD202611P101", "out", new BigDecimal("500"), null, null);
        assertEquals("Bỏ qua: không phải tiền vào", service.handle(out, "ip"));
        assertEquals("Bỏ qua: thiếu id hoặc số tiền", service.handle(in(7, "0"), "ip"));
        assertEquals("Bỏ qua: thiếu id hoặc số tiền", service.handle(null, "ip"));
        verify(payments, never()).confirmSepayTransfer(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
    }

    @Test
    void businessErrorsAreSwallowedSoSepayDoesNotRetryForever() {
        when(payments.confirmSepayTransfer(eq(8L), any(), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "Chua co chu tro dang hoat dong"));
        assertEquals("Bỏ qua: Chua co chu tro dang hoat dong", service.handle(in(8, "1000"), "ip"));
    }

    @Test
    void infrastructureErrorsPropagateSoSepayRetries() {
        when(payments.confirmSepayTransfer(eq(9L), any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.handle(in(9, "1000"), "ip"));
    }
}
