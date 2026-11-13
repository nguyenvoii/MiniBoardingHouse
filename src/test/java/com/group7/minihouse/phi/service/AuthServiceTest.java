package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.LoginRequest;
import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.repository.TenantReadRepository;
import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.JwtService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test thuan voi Mockito (khong can DB): dang nhap nguoi thue/nhan vien va tenantId trong token. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock UserActivityLogRepository logRepository;
    @Mock TenantReadRepository tenantReadRepository;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    final JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234567890", 60);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, userRoleRepository, logRepository, encoder, jwt,
                tenantReadRepository);
    }

    private User user(String email, String phone) {
        User u = new User();
        u.setUserId(5);
        u.setEmail(email);
        u.setPhoneNumber(phone);
        u.setPasswordHash(encoder.encode("pw"));
        u.setFullName("Nguoi Dung");
        u.setStatus("ACTIVE");
        return u;
    }

    private Tenant tenant(int id) {
        Tenant t = new Tenant();
        t.setTenantId(id);
        return t;
    }

    @Test
    void tenantLoginCarriesTenantId() {
        when(userRepository.findByEmailOrPhoneNumber("0901234567", "0901234567"))
                .thenReturn(Optional.of(user(null, "0901234567")));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));
        when(tenantReadRepository.findByPhone("0901234567")).thenReturn(Optional.of(tenant(42)));

        LoginResponse res = service.login(new LoginRequest("0901234567", "pw"), "127.0.0.1");

        assertEquals(42, res.user().tenantId());
        assertEquals(42, jwt.parse(res.accessToken()).tenantId());
    }

    @Test
    void staffLoginHasNullTenantId() {
        when(userRepository.findByEmailOrPhoneNumber("m@x.vn", "m@x.vn"))
                .thenReturn(Optional.of(user("m@x.vn", null)));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("MANAGER"));

        LoginResponse res = service.login(new LoginRequest("m@x.vn", "pw"), "127.0.0.1");

        assertNull(res.user().tenantId());
        verifyNoInteractions(tenantReadRepository);
    }

    @Test
    void tenantWithoutProfileGetsClearError() {
        when(userRepository.findByEmailOrPhoneNumber("0901234567", "0901234567"))
                .thenReturn(Optional.of(user(null, "0901234567")));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));
        when(tenantReadRepository.findByPhone("0901234567")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("0901234567", "pw"), "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("ho so khach thue"));
    }

    @Test
    void tenantWithNullPhoneGetsClearError() {
        when(userRepository.findByEmailOrPhoneNumber("t@x.vn", "t@x.vn"))
                .thenReturn(Optional.of(user("t@x.vn", null)));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(new LoginRequest("t@x.vn", "pw"), "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(tenantReadRepository);
    }

    @Test
    void tenantPhoneIsTrimmedBeforeLookup() {
        when(userRepository.findByEmailOrPhoneNumber("0901234567", "0901234567"))
                .thenReturn(Optional.of(user(null, " 0901234567 ")));
        when(userRoleRepository.findRoleCodesByUserId(5)).thenReturn(List.of("TENANT"));
        when(tenantReadRepository.findByPhone("0901234567")).thenReturn(Optional.of(tenant(42)));

        LoginResponse res = service.login(new LoginRequest("0901234567", "pw"), "127.0.0.1");

        assertEquals(42, res.user().tenantId());
    }
}
