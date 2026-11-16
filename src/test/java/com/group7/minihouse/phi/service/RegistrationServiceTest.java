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

import com.group7.minihouse.phi.dto.RegisterRequest;
import com.group7.minihouse.phi.repository.RoleRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Role;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.notification.NotificationSender;
import java.util.List;
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

/** UC1 dang ky chu tro, UC2 xac thuc email bang OTP (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock OtpService otpService;
    @Mock NotificationSender sender;
    @Mock ActivityLogService activityLogService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(userRepository, userRoleRepository, roleRepository, otpService, sender,
                encoder, activityLogService);
    }

    private RegisterRequest req(String name, String email, String phone, String pw) {
        return new RegisterRequest(name, email, phone, pw);
    }

    private User pending() {
        User u = new User();
        u.setUserId(9);
        u.setEmail("chu@x.vn");
        u.setFullName("Cu");
        u.setPasswordHash(encoder.encode("KeBiaTran1"));
        u.setStatus("INACTIVE");
        return u;
    }

    private void landlordRole() {
        Role r = new Role();
        r.setRoleId(1);
        r.setRoleCode("LANDLORD");
        when(roleRepository.findByRoleCode("LANDLORD")).thenReturn(Optional.of(r));
    }

    // ---------- UC1 ----------

    @Test
    void registerCreatesInactiveLandlordAndSendsEmailCode() {
        landlordRole();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(9);
            return u;
        });
        when(otpService.issue(any(User.class), eq("EMAIL"))).thenReturn("123456");

        service.register(req(" Nguyen Van A ", "chu@x.vn", "0901234567", "Passw0rdOk"), "1.1.1.1");

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        User saved = cap.getValue();
        assertEquals("INACTIVE", saved.getStatus());
        assertEquals("Nguyen Van A", saved.getFullName());
        assertTrue(encoder.matches("Passw0rdOk", saved.getPasswordHash()));
        ArgumentCaptor<UserRole> ur = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(ur.capture());
        assertEquals("LANDLORD", ur.getValue().getRole().getRoleCode());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("chu@x.vn"), anyString(), body.capture());
        assertTrue(body.getValue().contains("123456"));
    }

    @Test
    void registerRejectsBadInput() {
        for (RegisterRequest bad : List.of(
                req("", "chu@x.vn", null, "Passw0rdOk"),
                req("A", "khong-phai-email", null, "Passw0rdOk"),
                req("A", "chu@x.vn", "12ab", "Passw0rdOk"),
                req("A", "chu@x.vn", null, "yeu"))) {
            BusinessException e = assertThrows(BusinessException.class, () -> service.register(bad, "ip"));
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsEmailOfExistingActiveAccount() {
        User active = pending();
        active.setStatus("ACTIVE");
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(active));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register(req("A", "chu@x.vn", null, "Passw0rdOk"), "ip"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsPhoneOfAnotherAccount() {
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.empty());
        when(userRepository.existsByPhoneNumber("0901234567")).thenReturn(true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.register(req("A", "chu@x.vn", "0901234567", "Passw0rdOk"), "ip"));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @Test
    void registerAgainOverUnverifiedAccountOverwritesItSoSquatterCannotKeepPassword() {
        User old = pending();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(old));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("LANDLORD"));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(otpService.issue(old, "EMAIL")).thenReturn("222333");

        service.register(req("Chu That", "chu@x.vn", null, "Passw0rdOk"), "ip");

        assertEquals("Chu That", old.getFullName());
        assertTrue(encoder.matches("Passw0rdOk", old.getPasswordHash()));
        assertEquals("INACTIVE", old.getStatus());
        verify(sender).send(eq("chu@x.vn"), anyString(), anyString());
    }

    // ---------- UC2 ----------

    @Test
    void verifyEmailActivatesAccount() {
        User u = pending();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("LANDLORD"));
        when(otpService.verify(u, "EMAIL", "123456")).thenReturn(true);

        service.verifyEmail("chu@x.vn", "123456", "1.1.1.1");

        assertEquals("ACTIVE", u.getStatus());
        verify(userRepository).save(u);
        verify(activityLogService).log(9, "VERIFY_EMAIL", "Xac thuc email, kich hoat tai khoan", "1.1.1.1");
    }

    @Test
    void verifyEmailWrongCodeKeepsAccountInactive() {
        User u = pending();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("LANDLORD"));
        when(otpService.verify(u, "EMAIL", "000000")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyEmail("chu@x.vn", "000000", "ip"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("INACTIVE", u.getStatus());
    }

    @Test
    void verifyEmailDoesNotActivateNonLandlordOrNonInactiveAccounts() {
        User staff = pending(); // vd nhan vien bi admin cho ngung hoat dong: khong duoc tu kich hoat
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(staff));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("MANAGER"));

        assertThrows(BusinessException.class, () -> service.verifyEmail("chu@x.vn", "123456", "ip"));

        User locked = pending();
        locked.setStatus("LOCKED");
        when(userRepository.findByEmailOrPhoneNumber("khoa@x.vn", "khoa@x.vn")).thenReturn(Optional.of(locked));
        assertThrows(BusinessException.class, () -> service.verifyEmail("khoa@x.vn", "123456", "ip"));
        verify(otpService, never()).verify(any(), anyString(), anyString());
    }

    @Test
    void verifyEmailUnknownAccountGivesSameError() {
        when(userRepository.findByEmailOrPhoneNumber("no@x.vn", "no@x.vn")).thenReturn(Optional.empty());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyEmail("no@x.vn", "123456", "ip"));
        assertEquals("Ma xac thuc khong dung hoac da het han", e.getMessage());
    }

    @Test
    void resendSendsOnlyToPendingLandlordAndIsSilentOtherwise() {
        User u = pending();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("LANDLORD"));
        when(otpService.issue(u, "EMAIL")).thenReturn("999000");
        service.resendVerification("chu@x.vn");
        verify(sender).send(eq("chu@x.vn"), anyString(), anyString());

        when(userRepository.findByEmailOrPhoneNumber("no@x.vn", "no@x.vn")).thenReturn(Optional.empty());
        service.resendVerification("no@x.vn"); // khong nem loi
    }

    @Test
    void resendCooldownIsSwallowed() {
        User u = pending();
        when(userRepository.findByEmailOrPhoneNumber("chu@x.vn", "chu@x.vn")).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("LANDLORD"));
        when(otpService.issue(u, "EMAIL")).thenThrow(new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "cho"));

        service.resendVerification("chu@x.vn");
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }
}
