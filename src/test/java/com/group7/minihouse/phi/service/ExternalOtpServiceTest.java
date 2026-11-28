package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Trang thai ma OTP cua eOTP: cho kiem, giay phep sau khi dung, cooldown (khong goi mang that). */
@ExtendWith(MockitoExtension.class)
class ExternalOtpServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-28T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Mock EotpClient client;
    final MutableClock clock = new MutableClock();
    ExternalOtpService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new ExternalOtpService(client, clock);
        user = new User();
        user.setUserId(3);
    }

    private void sendOk() {
        when(client.send("0901234567")).thenReturn(new EotpClient.Sent("r1", 300));
        service.send(user, "RECOVERY", "0901234567");
    }

    @Test
    void sendKeepsRequestIdAndHandlesTheAccount() {
        assertFalse(service.handles(user, "RECOVERY"));
        sendOk();
        assertTrue(service.handles(user, "RECOVERY"));
        assertFalse(service.handles(user, "PHONE")); // moi loai ma co trang thai rieng
    }

    @Test
    void secondSendWithinAMinuteIsRejectedThenAllowed() {
        sendOk();
        BusinessException e = assertThrows(BusinessException.class, () -> service.send(user, "RECOVERY", "0901234567"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        verify(client, times(1)).send("0901234567");

        clock.advance(Duration.ofSeconds(61));
        service.send(user, "RECOVERY", "0901234567");
        verify(client, times(2)).send("0901234567");
    }

    @Test
    void failedSendDoesNotStartTheCooldown() {
        when(client.send("0901234567")).thenThrow(new BusinessException(HttpStatus.BAD_GATEWAY, "loi"))
                .thenReturn(new EotpClient.Sent("r1", 300));
        assertThrows(BusinessException.class, () -> service.send(user, "RECOVERY", "0901234567"));
        service.send(user, "RECOVERY", "0901234567"); // xin lai ngay duoc
        assertTrue(service.handles(user, "RECOVERY"));
    }

    @Test
    void correctCodeBecomesAOneTimeGrantForTheNextStep() {
        sendOk();
        when(client.verify("r1", "123456")).thenReturn(EotpClient.Verdict.OK);

        assertTrue(service.verify(user, "RECOVERY", "123456"));
        assertTrue(service.handles(user, "RECOVERY")); // con giay phep
        assertTrue(service.verify(user, "RECOVERY", "123456")); // goi lai cung ma khong hoi eOTP lan nua
        verify(client, times(1)).verify("r1", "123456");

        assertFalse(service.consume(user, "RECOVERY", "000000")); // sai ma
        assertTrue(service.consume(user, "RECOVERY", "123456"));
        assertFalse(service.consume(user, "RECOVERY", "123456")); // chi dung 1 lan
        assertFalse(service.handles(user, "RECOVERY"));
    }

    @Test
    void wrongCodeKeepsWaitingForAnotherTry() {
        sendOk();
        when(client.verify("r1", "000000")).thenReturn(EotpClient.Verdict.WRONG_CODE);
        when(client.verify("r1", "123456")).thenReturn(EotpClient.Verdict.OK);

        assertFalse(service.verify(user, "RECOVERY", "000000"));
        assertTrue(service.handles(user, "RECOVERY")); // eOTP tu dem 5 lan sai, app khong huy som
        assertTrue(service.verify(user, "RECOVERY", "123456"));
    }

    @Test
    void expiredOrLockedCodeIsDropped() {
        sendOk();
        when(client.verify("r1", "123456")).thenReturn(EotpClient.Verdict.TOO_MANY);

        assertFalse(service.verify(user, "RECOVERY", "123456"));
        assertFalse(service.handles(user, "RECOVERY"));
    }

    @Test
    void codeExpiredByTimeIsRejectedWithoutCallingEotp() {
        sendOk();
        clock.advance(Duration.ofSeconds(301));

        assertFalse(service.handles(user, "RECOVERY"));
        assertFalse(service.verify(user, "RECOVERY", "123456"));
        verify(client, never()).verify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void grantExpiresAfterTenMinutes() {
        sendOk();
        when(client.verify("r1", "123456")).thenReturn(EotpClient.Verdict.OK);
        service.verify(user, "RECOVERY", "123456");

        clock.advance(Duration.ofMinutes(11));

        assertFalse(service.consume(user, "RECOVERY", "123456"));
    }
}
