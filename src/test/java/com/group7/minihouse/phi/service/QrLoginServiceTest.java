package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.group7.minihouse.phi.dto.LoginResponse;
import com.group7.minihouse.phi.repository.TenantReadRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/** Ma QR dang nhap cho nguoi thue: chi dung duoc khi con mat khau tam, mat hieu luc khi doi/cap lai mat khau. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QrLoginServiceTest {

    @Mock TenantReadRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock ActivityLogService activityLogService;

    final JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234567890", 60);
    QrLoginService service;
    User account;

    @BeforeEach
    void setUp() {
        service = new QrLoginService(tenantRepository, userRepository, userRoleRepository, activityLogService, jwt);
        Tenant t = new Tenant();
        t.setTenantId(42);
        t.setPhoneNumber("0901234567");
        account = new User();
        account.setUserId(9);
        account.setPhoneNumber("0901234567");
        account.setFullName("Tran An");
        account.setStatus("ACTIVE");
        account.setPasswordHash("hash-cua-mat-khau-tam");
        account.setMustChangePassword(true);
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(Optional.of(account));
        when(userRepository.findById(9)).thenReturn(Optional.of(account));
        when(userRoleRepository.findRoleCodesByUserId(9)).thenReturn(List.of("TENANT"));
    }

    @Test
    void scanningIssuedCodeLogsTheTenantIn() {
        LoginResponse r = service.login(service.issue(42).token(), "ip");
        assertEquals(42, r.user().tenantId());
        assertTrue(r.mustChangePassword()); // van phai doi mat khau tam
    }

    @Test
    void codeDiesWhenTenantSetsOwnPassword() {
        String token = service.issue(42).token();
        account.setMustChangePassword(false);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, () -> service.login(token, "ip")).getStatus());
    }

    @Test
    void codeDiesWhenTempPasswordIsReissued() {
        String token = service.issue(42).token();
        account.setPasswordHash("hash-cua-mat-khau-tam-moi");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, () -> service.login(token, "ip")).getStatus());
    }

    @Test
    void lockedAccountCannotUseCode() {
        String token = service.issue(42).token();
        account.setStatus("LOCKED");
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(BusinessException.class, () -> service.login(token, "ip")).getStatus());
    }

    @Test
    void garbageOrSessionTokenIsRejected() {
        assertThrows(BusinessException.class, () -> service.login("khong-phai-token", "ip"));
        assertThrows(BusinessException.class, () -> service.login(null, "ip"));
    }

    @Test
    void noCodeForTenantWhoAlreadyChoseOwnPassword() {
        account.setMustChangePassword(false);
        assertEquals(HttpStatus.CONFLICT, assertThrows(BusinessException.class, () -> service.issue(42)).getStatus());
    }
}
