package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.TenantAccountResponse;
import com.group7.minihouse.phi.repository.RoleRepository;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Role;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.exception.BusinessException;
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

/** UC10: cap tai khoan dang nhap cho nguoi thue (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class TenantAccountServiceTest {

    @Mock TenantReadRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    TenantAccountService service;

    @BeforeEach
    void setUp() {
        service = new TenantAccountService(tenantRepository, userRepository, userRoleRepository,
                roleRepository, encoder);
    }

    private Tenant tenant(String phone, String email) {
        Tenant t = new Tenant();
        t.setTenantId(42);
        t.setFullName("Tran An");
        t.setPhoneNumber(phone);
        t.setEmail(email);
        return t;
    }

    private Role tenantRole() {
        Role r = new Role();
        r.setRoleId(3);
        r.setRoleCode("TENANT");
        return r;
    }

    private void saveAssignsId() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(9);
            return u;
        });
    }

    @Test
    void createsUserWithTenantRoleAndTempPassword() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant(" 0901234567 ", "t@x.vn")));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        TenantAccountResponse res = service.createAccount(42);

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(u.capture());
        assertEquals("0901234567", u.getValue().getPhoneNumber());
        assertEquals("t@x.vn", u.getValue().getEmail());
        assertEquals("Tran An", u.getValue().getFullName());
        assertEquals("ACTIVE", u.getValue().getStatus());
        assertEquals(10, res.temporaryPassword().length());
        assertTrue(encoder.matches(res.temporaryPassword(), u.getValue().getPasswordHash()));
        assertEquals(9, res.userId());
        assertEquals("0901234567", res.phoneNumber());

        ArgumentCaptor<UserRole> ur = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(ur.capture());
        assertEquals("TENANT", ur.getValue().getRole().getRoleCode());
        assertEquals(9, ur.getValue().getId().getUserId());
        assertEquals(3, ur.getValue().getId().getRoleId());
    }

    @Test
    void rejectsSecondAccountForSamePhone() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(userRepository.existsByPhoneNumber("0901234567")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsEmailAlreadyUsedByAnotherAccount() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", "t@x.vn")));
        when(userRepository.existsByEmail("t@x.vn")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsTenantProfileWithBlankPhone() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("   ", null)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("so dien thoai"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void tenantNotFound() {
        when(tenantRepository.findById(99)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(99));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void failsClearlyWhenTenantRoleNotSeeded() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("seed.sql"));
    }

    @Test
    void blankEmailIsTreatedAsNoEmail() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", "   ")));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        service.createAccount(42);

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(u.capture());
        assertNull(u.getValue().getEmail());
        verify(userRepository, never()).existsByEmail(any());
    }
}
