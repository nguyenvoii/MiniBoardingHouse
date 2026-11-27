package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.notification.NotificationSender;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** UC4 quen mat khau (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    @Mock UserRepository userRepository;
    @Mock OtpService otpService;
    @Mock NotificationSender sender;
    @Mock ActivityLogService activityLogService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(userRepository, otpService, sender, encoder, activityLogService);
    }

    private User user(String status) {
        User u = new User();
        u.setUserId(3);
        u.setEmail("a@x.vn");
        u.setPhoneNumber("0901234567");
        u.setFullName("A");
        u.setPasswordHash(encoder.encode("OldPass1x"));
        u.setStatus(status);
        return u;
    }

    // ---------- forgot ----------

    @Test
    void forgotSendsCodeToEmail() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.issue(u, "RECOVERY")).thenReturn("654321");

        service.forgotPassword("a@x.vn", "1.1.1.1");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).sendHtml(eq("a@x.vn"), anyString(), body.capture(), anyString());
        assertTrue(body.getValue().contains("654321"));
    }

    @Test
    void forgotByPhoneStillSendsToEmailWhenAccountHasOne() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("0901234567", "0901234567")).thenReturn(Optional.of(u));
        when(otpService.issue(u, "RECOVERY")).thenReturn("111222");

        service.forgotPassword("0901234567", "1.1.1.1");

        verify(sender).sendHtml(eq("a@x.vn"), anyString(), anyString(), anyString());
    }

    @Test
    void forgotFallsBackToPhoneWhenNoEmail() {
        User u = user("ACTIVE");
        u.setEmail(null);
        when(userRepository.findByEmailOrPhoneNumber("0901234567", "0901234567")).thenReturn(Optional.of(u));
        when(otpService.issue(u, "RECOVERY")).thenReturn("111222");

        service.forgotPassword("0901234567", "1.1.1.1");

        verify(sender).sendHtml(eq("0901234567"), anyString(), anyString(), anyString());
    }

    @Test
    void forgotUnknownAccountDoesNothingAndDoesNotThrow() {
        when(userRepository.findByEmailOrPhoneNumber("no@x.vn", "no@x.vn")).thenReturn(Optional.empty());

        service.forgotPassword("no@x.vn", "1.1.1.1");

        verify(sender, never()).sendHtml(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void forgotLockedAccountDoesNothing() {
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(user("LOCKED")));

        service.forgotPassword("a@x.vn", "1.1.1.1");

        verify(otpService, never()).issue(any(), anyString());
        verify(sender, never()).sendHtml(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void forgotCooldownIsSwallowedSoResponseDoesNotLeakAccountExistence() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.issue(u, "RECOVERY"))
                .thenThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "cho"));

        service.forgotPassword("a@x.vn", "1.1.1.1"); // khong nem loi

        verify(sender, never()).sendHtml(anyString(), anyString(), anyString(), anyString());
    }

    // ---------- verify code (buoc nhap ma) ----------

    @Test
    void verifyCodeAcceptsCorrectCodeWithoutChangingPassword() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.check(u, "RECOVERY", "654321")).thenReturn(true);

        service.verifyCode("a@x.vn", "654321");

        assertTrue(encoder.matches("OldPass1x", u.getPasswordHash()));
        verify(userRepository, never()).save(any());
        verify(otpService, never()).verify(any(), anyString(), anyString()); // chua dung het ma
    }

    @Test
    void verifyCodeWrongCodeOrUnknownAccountGiveTheSameError() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.check(u, "RECOVERY", "000000")).thenReturn(false);
        when(userRepository.findByEmailOrPhoneNumber("no@x.vn", "no@x.vn")).thenReturn(Optional.empty());

        BusinessException wrong = assertThrows(BusinessException.class, () -> service.verifyCode("a@x.vn", "000000"));
        BusinessException unknown = assertThrows(BusinessException.class, () -> service.verifyCode("no@x.vn", "123456"));

        assertEquals(HttpStatus.BAD_REQUEST, wrong.getStatus());
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void forgotMailCarriesCodeInSubjectAndBothBodies() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.issue(u, "RECOVERY")).thenReturn("654321");

        service.forgotPassword("a@x.vn", "1.1.1.1");

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(sender).sendHtml(eq("a@x.vn"), subject.capture(), anyString(), html.capture());
        assertTrue(subject.getValue().contains("654321"));
        assertTrue(html.getValue().contains("654321"));
        assertTrue(!html.getValue().contains("{{"), "con sot cho trong mau thu");
    }

    // ---------- reset ----------

    @Test
    void resetChangesPasswordAndLogsIt() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.verify(u, "RECOVERY", "654321")).thenReturn(true);

        service.resetPassword("a@x.vn", "654321", "NewPass2y", "1.1.1.1");

        assertTrue(encoder.matches("NewPass2y", u.getPasswordHash()));
        verify(userRepository).save(u);
        verify(activityLogService).log(3, "RESET_PASSWORD", "Dat lai mat khau bang ma OTP", "1.1.1.1");
    }

    @Test
    void resetWithWrongCodeIsRejectedAndPasswordKept() {
        User u = user("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(u));
        when(otpService.verify(u, "RECOVERY", "000000")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("a@x.vn", "000000", "NewPass2y", "1.1.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertTrue(encoder.matches("OldPass1x", u.getPasswordHash()));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetUnknownAccountGivesSameErrorAsWrongCode() {
        when(userRepository.findByEmailOrPhoneNumber("no@x.vn", "no@x.vn")).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("no@x.vn", "123456", "NewPass2y", "1.1.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("Ma xac thuc khong dung hoac da het han", e.getMessage());
    }

    @Test
    void resetLockedAccountIsRejected() {
        when(userRepository.findByEmailOrPhoneNumber("a@x.vn", "a@x.vn")).thenReturn(Optional.of(user("LOCKED")));

        assertThrows(BusinessException.class,
                () -> service.resetPassword("a@x.vn", "123456", "NewPass2y", "1.1.1.1"));
        verify(otpService, never()).verify(any(), anyString(), anyString());
    }

    @Test
    void resetWeakPasswordIsRejectedWithoutBurningTheCode() {
        // kiem tra mat khau chay TRUOC khi tra tai khoan (khong lo tai khoan co ton tai hay khong)
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("a@x.vn", "654321", "abc", "1.1.1.1"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verify(otpService, never()).verify(any(), anyString(), anyString());
    }
}
