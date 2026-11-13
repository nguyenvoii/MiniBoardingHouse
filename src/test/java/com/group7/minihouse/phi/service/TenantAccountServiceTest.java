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
import java.time.LocalDateTime;
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

/** UC10: cap tai khoan dang nhap cho nguoi thue (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class TenantAccountServiceTest {

    @Mock TenantReadRepository tenantRepository;
    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock ActivityLogService activityLogService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    TenantAccountService service;

    @BeforeEach
    void setUp() {
        service = new TenantAccountService(tenantRepository, userRepository, userRoleRepository,
                roleRepository, encoder, activityLogService);
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

        TenantAccountResponse res = service.createAccount(42, 1, "127.0.0.1");

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

    private User existingUser(int id, String phone, String email, LocalDateTime createdAt) {
        User u = new User();
        u.setUserId(id);
        u.setPhoneNumber(phone);
        u.setEmail(email);
        u.setStatus("ACTIVE");
        u.setCreatedAt(createdAt);
        return u;
    }

    // ---------- khoa / mo khoa tai khoan nguoi thue ----------

    private void tenantWithAccount(String... accountRoles) {
        Tenant t = tenant(" 0901234567 ", null);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(
                Optional.of(existingUser(7, "0901234567", null, LocalDateTime.of(2026, 2, 1, 0, 0))));
        when(userRoleRepository.findRoleCodesByUserId(7)).thenReturn(List.of(accountRoles));
    }

    @Test
    void locksAndUnlocksTenantAccountAndLogsBoth() {
        tenantWithAccount("TENANT");

        service.setAccountStatus(42, "LOCKED", 1, "ip");
        verify(activityLogService).log(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("LOCK_USER"), any(String.class),
                org.mockito.ArgumentMatchers.eq("ip"));

        service.setAccountStatus(42, "ACTIVE", 1, "ip");
        verify(activityLogService).log(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("UNLOCK_USER"), any(String.class),
                org.mockito.ArgumentMatchers.eq("ip"));
    }

    @Test
    void accountStatusActuallyChanges() {
        Tenant t = tenant("0901234567", null);
        User account = existingUser(7, "0901234567", null, LocalDateTime.of(2026, 2, 1, 0, 0));
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(Optional.of(account));
        when(userRoleRepository.findRoleCodesByUserId(7)).thenReturn(List.of("TENANT"));

        service.setAccountStatus(42, "LOCKED", 1, "ip");

        assertEquals("LOCKED", account.getStatus());
        verify(userRepository).save(account);
    }

    @Test
    void tenantWithoutAccountOrUnknownTenantIs404() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(tenantRepository.findById(99)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.setAccountStatus(42, "LOCKED", 1, "ip")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.setAccountStatus(99, "LOCKED", 1, "ip")).getStatus());
    }

    @Test
    void staffUsingTheTenantsPhoneCannotBeLockedThroughTenantApi() {
        tenantWithAccount("MANAGER");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.setAccountStatus(42, "LOCKED", 1, "ip"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    /** Tai khoan tao TRUOC ho so (SDT bi tai su dung) khong phai tai khoan cua khach thue nay. */
    @Test
    void orphanAccountIsNotTheTenantsAccount() {
        Tenant t = tenant("0901234567", null);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(
                Optional.of(existingUser(7, "0901234567", null, LocalDateTime.of(2025, 1, 1, 0, 0))));
        when(userRoleRepository.findRoleCodesByUserId(7)).thenReturn(List.of("TENANT"));

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.setAccountStatus(42, "LOCKED", 1, "ip")).getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void onlyActiveOrLockedAreAcceptedForTenantAccounts() {
        for (String bad : List.of("INACTIVE", "x", "", "locked")) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class,
                    () -> service.setAccountStatus(42, bad, 1, "ip")).getStatus(), bad);
        }
    }

    @Test
    void logsWhoCreatedTheTenantAccount() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        service.createAccount(42, 1, "10.0.0.1");

        verify(activityLogService).log(org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("CREATE_TENANT_ACCOUNT"), any(String.class),
                org.mockito.ArgumentMatchers.eq("10.0.0.1"));
    }

    @Test
    void doesNotLogWhenThereIsNoActor() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        service.createAccount(42, null, "10.0.0.1");

        org.mockito.Mockito.verifyNoInteractions(activityLogService);
    }

    @Test
    void rejectsSecondAccountForSamePhone() {
        Tenant t = tenant("0901234567", null);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(
                Optional.of(existingUser(7, "0901234567", null, LocalDateTime.of(2026, 2, 1, 0, 0))));
        when(userRoleRepository.findRoleCodesByUserId(7)).thenReturn(List.of("TENANT"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42, 1, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("da co tai khoan"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void staffUsingTheSamePhoneGetsAccurateMessage() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(
                Optional.of(existingUser(3, "0901234567", "m@x.vn", LocalDateTime.of(2025, 1, 1, 0, 0))));
        when(userRoleRepository.findRoleCodesByUserId(3)).thenReturn(List.of("MANAGER"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42, 1, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("nhan vien"));
        verify(userRepository, never()).save(any(User.class));
    }

    /** SDT cua khach thue cu bi tai su dung cho khach moi: tai khoan mo coi bi vo hieu hoa, khach moi duoc cap tai khoan. */
    @Test
    void retiresOrphanAccountWhenPhoneIsReusedByNewTenant() {
        Tenant t = tenant("0901234567", null);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        User orphan = existingUser(7, "0901234567", "old@x.vn", LocalDateTime.of(2025, 1, 1, 0, 0));
        when(tenantRepository.findById(42)).thenReturn(Optional.of(t));
        when(userRepository.findByPhoneNumber("0901234567")).thenReturn(Optional.of(orphan));
        when(userRoleRepository.findRoleCodesByUserId(7)).thenReturn(List.of("TENANT"));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        TenantAccountResponse res = service.createAccount(42, 1, "127.0.0.1");

        // tai khoan cu phai duoc ghi xuong DB NGAY (flush) de nha cho unique index truoc khi them tai khoan moi
        ArgumentCaptor<User> flushed = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(flushed.capture());
        assertEquals("INACTIVE", flushed.getValue().getStatus());
        assertNull(flushed.getValue().getPhoneNumber());
        assertNull(flushed.getValue().getEmail());
        assertEquals(9, res.userId());
    }

    @Test
    void rejectsEmailAlreadyUsedByAnotherAccount() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", "t@x.vn")));
        when(userRepository.existsByEmail("t@x.vn")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42, 1, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsTenantProfileWithBlankPhone() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("   ", null)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42, 1, "127.0.0.1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("so dien thoai"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void tenantNotFound() {
        when(tenantRepository.findById(99)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(99, 1, "127.0.0.1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void failsClearlyWhenTenantRoleNotSeeded() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", null)));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createAccount(42, 1, "127.0.0.1"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("seed.sql"));
    }

    @Test
    void blankEmailIsTreatedAsNoEmail() {
        when(tenantRepository.findById(42)).thenReturn(Optional.of(tenant("0901234567", "   ")));
        when(roleRepository.findByRoleCode("TENANT")).thenReturn(Optional.of(tenantRole()));
        saveAssignsId();

        service.createAccount(42, 1, "127.0.0.1");

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(u.capture());
        assertNull(u.getValue().getEmail());
        verify(userRepository, never()).existsByEmail(any());
    }
}
