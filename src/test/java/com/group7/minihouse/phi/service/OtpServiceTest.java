package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.repository.UserVerificationRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserVerification;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Ma OTP dung chung cho UC1, UC2, UC4 (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 16, 10, 0);

    @Mock UserVerificationRepository repo;
    OtpService service;
    User user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new OtpService(repo, clock);
        user = new User();
        user.setUserId(7);
    }

    private UserVerification row(String code, LocalDateTime expiresAt) {
        UserVerification v = new UserVerification();
        v.setUser(user);
        v.setType("RECOVERY");
        v.setCode(code);
        v.setExpiresAt(expiresAt);
        v.setIsVerified(false);
        return v;
    }

    /** Moc "cap sau thoi diem nay" = bay gio - 60 giay, quy ra han dung = + 10 phut. */
    static final LocalDateTime COOLDOWN_EDGE = NOW.minusSeconds(60).plusMinutes(10);

    @Test
    void issueCreatesSixDigitCodeValidTenMinutes() {
        when(repo.countIssuedAfter(7, "RECOVERY", COOLDOWN_EDGE)).thenReturn(0L);
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of());

        String code = service.issue(user, "RECOVERY");

        assertTrue(code.matches("\\d{6}"));
        ArgumentCaptor<UserVerification> cap = ArgumentCaptor.forClass(UserVerification.class);
        verify(repo).save(cap.capture());
        assertEquals(code, cap.getValue().getCode());
        assertEquals(NOW.plusMinutes(10), cap.getValue().getExpiresAt());
        assertFalse(cap.getValue().getIsVerified());
        assertEquals("RECOVERY", cap.getValue().getType());
    }

    @Test
    void issueTooSoonAfterPreviousIsRejected() {
        // vua cap ma trong 60 giay qua (ke ca ma da bi huy do doan sai) => chua cho xin tiep
        when(repo.countIssuedAfter(7, "RECOVERY", COOLDOWN_EDGE)).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class, () -> service.issue(user, "RECOVERY"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }

    @Test
    void issueAfterCooldownInvalidatesOldCode() {
        UserVerification old = row("111111", NOW.plusMinutes(5)); // cap 5 phut truoc
        when(repo.countIssuedAfter(7, "RECOVERY", COOLDOWN_EDGE)).thenReturn(0L);
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of(old));

        service.issue(user, "RECOVERY");

        assertTrue(old.getIsVerified()); // ma cu khong dung duoc nua; han (expiresAt) giu nguyen de tinh cooldown
        assertEquals(NOW.plusMinutes(5), old.getExpiresAt());
    }

    @Test
    void verifyCorrectCodeConsumesIt() {
        UserVerification v = row("123456", NOW.plusMinutes(3));
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of(v));

        assertTrue(service.verify(user, "RECOVERY", "123456"));
        assertTrue(v.getIsVerified());
    }

    @Test
    void verifyWrongCodeBurnsOutstandingCodes() {
        UserVerification v = row("123456", NOW.plusMinutes(3));
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of(v));

        assertFalse(service.verify(user, "RECOVERY", "000000"));
        assertTrue(v.getIsVerified()); // doan sai 1 lan la ma bi huy, phai xin ma moi
        assertEquals(NOW.plusMinutes(3), v.getExpiresAt()); // giu han de cooldown van tinh dung
    }

    @Test
    void verifyWithNoActiveCodeIsFalse() {
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of());
        assertFalse(service.verify(user, "RECOVERY", "123456"));
        verify(repo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void verifyNullCodeIsFalse() {
        UserVerification v = row("123456", NOW.plusMinutes(3));
        when(repo.findActive(7, "RECOVERY", NOW)).thenReturn(List.of(v));
        assertFalse(service.verify(user, "RECOVERY", null));
    }
}
