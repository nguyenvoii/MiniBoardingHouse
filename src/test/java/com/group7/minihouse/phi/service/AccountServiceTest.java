package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.ProfileResponse;
import com.group7.minihouse.phi.dto.UpdateProfileRequest;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
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

/** UC5 doi mat khau, UC6 ho so ca nhan (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock ActivityLogService activityLogService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(userRepository, userRoleRepository, encoder, activityLogService);
    }

    private User user(int id, String phone) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nguyen Van A");
        u.setEmail("a@x.vn");
        u.setPhoneNumber(phone);
        u.setPasswordHash(encoder.encode("OldPass1x"));
        u.setStatus("ACTIVE");
        return u;
    }

    private BusinessException fails(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    // ---------- UC5 ----------

    @Test
    void changesPasswordAndLogsIt() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));

        service.changePassword(5, "OldPass1x", "NewPass2y", "1.1.1.1");

        assertTrue(encoder.matches("NewPass2y", u.getPasswordHash()));
        verify(userRepository).save(u);
        verify(activityLogService).log(5, "CHANGE_PASSWORD", "Doi mat khau", "1.1.1.1");
    }

    @Test
    void rejectsWrongCurrentPassword() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));

        BusinessException ex = fails(() -> service.changePassword(5, "sai-roi", "NewPass2y", "ip"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(encoder.matches("OldPass1x", u.getPasswordHash()));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsWeakNewPasswords() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));

        for (String weak : List.of("Ab1", "abcdefgh", "12345678", "        ")) {
            BusinessException ex = fails(() -> service.changePassword(5, "OldPass1x", weak, "ip"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), weak);
        }
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsNewPasswordEqualToCurrent() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));

        BusinessException ex = fails(() -> service.changePassword(5, "OldPass1x", "OldPass1x", "ip"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("khac"));
    }

    @Test
    void changePasswordForUnknownUserIs404() {
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, fails(() -> service.changePassword(99, "a", "b", "ip")).getStatus());
    }

    // ---------- UC6 ----------

    @Test
    void returnsProfileWithRoles() {
        when(userRepository.findById(5)).thenReturn(Optional.of(user(5, "0901234567")));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        ProfileResponse p = service.getProfile(5);

        assertEquals("Nguyen Van A", p.fullName());
        assertEquals("0901234567", p.phoneNumber());
        assertEquals(List.of("MANAGER"), p.roles());
    }

    @Test
    void updatesNameAndPhoneAndLogsIt() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        ProfileResponse p = service.updateProfile(5, new UpdateProfileRequest("  Tran B ", " 0912345678 "), "ip");

        assertEquals("Tran B", u.getFullName());
        assertEquals("0912345678", u.getPhoneNumber());
        assertEquals("0912345678", p.phoneNumber());
        verify(userRepository).save(u);
        verify(activityLogService).log(5, "UPDATE_PROFILE", "Cap nhat ho so ca nhan", "ip");
    }

    @Test
    void rejectsPhoneUsedByAnotherUser() {
        User u = user(5, null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));
        when(userRepository.findByPhoneNumber("0912345678")).thenReturn(Optional.of(user(7, "0912345678")));

        BusinessException ex = fails(() -> service.updateProfile(5, new UpdateProfileRequest("A", "0912345678"), "ip"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void ownPhoneIsNotAConflict() {
        User u = user(5, "0912345678");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        service.updateProfile(5, new UpdateProfileRequest("Doi ten", "0912345678"), "ip");

        assertEquals("Doi ten", u.getFullName());
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    /** Review Focus 1: tai khoan nguoi thue noi voi ho so khach thue bang SDT, nen khong duoc tu doi SDT. */
    @Test
    void tenantCannotChangePhone() {
        User u = user(5, "0901234567");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));

        BusinessException ex = fails(() -> service.updateProfile(5, new UpdateProfileRequest("A", "0999999999"), "ip"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("chu tro"));
        assertEquals("0901234567", u.getPhoneNumber());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void tenantCannotClearPhone() {
        when(userRepository.findById(5)).thenReturn(Optional.of(user(5, "0901234567")));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));

        assertEquals(HttpStatus.CONFLICT,
                fails(() -> service.updateProfile(5, new UpdateProfileRequest("A", "  "), "ip")).getStatus());
    }

    @Test
    void tenantMayResendTheSamePhoneAndChangeName() {
        User u = user(5, "0901234567");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));

        service.updateProfile(5, new UpdateProfileRequest("Ten Moi", " 0901234567 "), "ip");

        assertEquals("Ten Moi", u.getFullName());
        assertEquals("0901234567", u.getPhoneNumber());
    }

    @Test
    void rejectsMalformedPhone() {
        when(userRepository.findById(5)).thenReturn(Optional.of(user(5, null)));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        for (String bad : List.of("abc", "12345", "09 1234 5678", "0912-345-678")) {
            BusinessException ex = fails(() -> service.updateProfile(5, new UpdateProfileRequest("A", bad), "ip"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), bad);
        }
    }

    @Test
    void blankPhoneBecomesNullForStaff() {
        User u = user(5, "0912345678");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        service.updateProfile(5, new UpdateProfileRequest("A", "   "), "ip");

        ArgumentCaptor<User> c = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(c.capture());
        assertNull(c.getValue().getPhoneNumber());
    }

    /** Khong gui phoneNumber = giu nguyen (chi gui "" moi la xoa), tranh vo y mat SDT khi chi sua ten. */
    @Test
    void absentPhoneMeansUnchanged() {
        User u = user(5, "0912345678");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        service.updateProfile(5, new UpdateProfileRequest("Ten Moi", null), "ip");

        assertEquals("Ten Moi", u.getFullName());
        assertEquals("0912345678", u.getPhoneNumber());
        verify(userRepository, never()).findByPhoneNumber(any());
    }

    /** Khong de nhan vien tu xoa SDT khi khong co email: se het cach dang nhap. */
    @Test
    void clearingPhoneIsRejectedWhenThereIsNoEmail() {
        User u = user(5, "0912345678");
        u.setEmail(null);
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        BusinessException ex = fails(() -> service.updateProfile(5, new UpdateProfileRequest("A", ""), "ip"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("0912345678", u.getPhoneNumber());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void tenantSendingOnlyTheNameKeepsPhoneWithoutError() {
        User u = user(5, "0901234567");
        when(userRepository.findById(5)).thenReturn(Optional.of(u));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));

        service.updateProfile(5, new UpdateProfileRequest("Ten Khac", null), "ip");

        assertEquals("Ten Khac", u.getFullName());
        assertEquals("0901234567", u.getPhoneNumber());
    }

    @Test
    void rejectsBlankName() {
        when(userRepository.findById(5)).thenReturn(Optional.of(user(5, null)));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        assertEquals(HttpStatus.BAD_REQUEST,
                fails(() -> service.updateProfile(5, new UpdateProfileRequest("   ", null), "ip")).getStatus());
    }
}
