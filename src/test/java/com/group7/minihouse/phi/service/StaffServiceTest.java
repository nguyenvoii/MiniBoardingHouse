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

import com.group7.minihouse.phi.dto.CreateStaffRequest;
import com.group7.minihouse.phi.dto.StaffCreatedResponse;
import com.group7.minihouse.phi.dto.StaffResponse;
import com.group7.minihouse.phi.dto.UpdateStaffRequest;
import com.group7.minihouse.phi.repository.RoleRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.phi.repository.UserRoleRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.Role;
import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserRole;
import com.group7.minihouse.shared.entity.UserRoleId;
import com.group7.minihouse.shared.exception.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** UC8 them nhan vien, UC9 sua/khoa/mo khoa nhan vien (Mockito, khong can DB). */
@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserRoleRepository userRoleRepository;
    @Mock RoleRepository roleRepository;
    @Mock ActivityLogService activityLogService;

    final PasswordEncoder encoder = new BCryptPasswordEncoder();
    StaffService service;

    @BeforeEach
    void setUp() {
        service = new StaffService(userRepository, userRoleRepository, roleRepository, encoder, activityLogService);
    }

    private Role role(int id, String code) {
        Role r = new Role();
        r.setRoleId(id);
        r.setRoleCode(code);
        return r;
    }

    private void roleExists(String code, int id) {
        when(roleRepository.findByRoleCode(code)).thenReturn(Optional.of(role(id, code)));
    }

    private void saveAssignsId() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(9);
            return u;
        });
    }

    private User user(int id, String status) {
        User u = new User();
        u.setUserId(id);
        u.setFullName("Nhan Vien " + id);
        u.setEmail("nv" + id + "@x.vn");
        u.setStatus(status);
        return u;
    }

    private void isStaffWithRoles(int id, String status, String... roles) {
        when(userRepository.findById(id)).thenReturn(Optional.of(user(id, status)));
        when(userRoleRepository.findRoleCodesByUserId(id)).thenReturn(List.of(roles));
    }

    private BusinessException fails(Runnable r) {
        return assertThrows(BusinessException.class, r::run);
    }

    // ---------------- UC8: them nhan vien ----------------

    @Test
    void createsStaffWithTempPasswordAndRoles() {
        roleExists("MANAGER", 2);
        saveAssignsId();

        StaffCreatedResponse res = service.create(
                new CreateStaffRequest(" Le Van C ", " c@x.vn ", " 0933333333 ", List.of("MANAGER")), 1, "ip");

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(u.capture());
        assertEquals("Le Van C", u.getValue().getFullName());
        assertEquals("c@x.vn", u.getValue().getEmail());
        assertEquals("0933333333", u.getValue().getPhoneNumber());
        assertEquals("ACTIVE", u.getValue().getStatus());
        assertTrue(encoder.matches(res.temporaryPassword(), u.getValue().getPasswordHash()));
        assertEquals(10, res.temporaryPassword().length());

        ArgumentCaptor<UserRole> ur = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(ur.capture());
        assertEquals("MANAGER", ur.getValue().getRole().getRoleCode());
        assertEquals(new UserRoleId(9, 2), ur.getValue().getId());
        assertEquals(9, res.staff().userId());
        assertEquals(List.of("MANAGER"), res.staff().roles());
        verify(activityLogService).log(eq(1), eq("CREATE_STAFF"), anyString(), eq("ip"));
    }

    @Test
    void createRequiresEmailOrPhone() {
        BusinessException ex = fails(() -> service.create(
                new CreateStaffRequest("A", "  ", null, List.of("MANAGER")), 1, "ip"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void createRejectsBadEmailAndPhoneFormat() {
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.create(
                new CreateStaffRequest("A", "khong-phai-email", null, List.of("MANAGER")), 1, "ip")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.create(
                new CreateStaffRequest("A", null, "abc", List.of("MANAGER")), 1, "ip")).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.create(
                new CreateStaffRequest("  ", "a@x.vn", null, List.of("MANAGER")), 1, "ip")).getStatus());
    }

    @Test
    void createRejectsInvalidRoleSets() {
        for (List<String> bad : List.of(List.<String>of(), List.of("LANDLORD"), List.of("TENANT"),
                List.of("MANAGER", "LANDLORD"), List.of("BOSS"))) {
            BusinessException ex = fails(() -> service.create(
                    new CreateStaffRequest("A", "a@x.vn", null, bad), 1, "ip"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus(), bad.toString());
        }
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.create(
                new CreateStaffRequest("A", "a@x.vn", null, null), 1, "ip")).getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createRejectsDuplicateEmailAndPhone() {
        when(userRepository.existsByEmail("c@x.vn")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, fails(() -> service.create(
                new CreateStaffRequest("A", "c@x.vn", null, List.of("MANAGER")), 1, "ip")).getStatus());

        when(userRepository.existsByPhoneNumber("0933333333")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, fails(() -> service.create(
                new CreateStaffRequest("A", null, "0933333333", List.of("MANAGER")), 1, "ip")).getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createFailsClearlyWhenRoleNotSeeded() {
        when(roleRepository.findByRoleCode("CASHIER")).thenReturn(Optional.empty());

        BusinessException ex = fails(() -> service.create(
                new CreateStaffRequest("A", "a@x.vn", null, List.of("CASHIER")), 1, "ip"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        assertTrue(ex.getMessage().contains("seed.sql"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------- UC9: sua thong tin ----------------

    @Test
    void updatesInfoAndReplacesRoles() {
        isStaffWithRoles(7, "ACTIVE", "CASHIER");
        UserRole oldRole = new UserRole();
        oldRole.setRole(role(3, "CASHIER"));
        when(userRoleRepository.findByUserUserId(7)).thenReturn(List.of(oldRole));
        roleExists("MANAGER", 2);

        StaffResponse res = service.update(7,
                new UpdateStaffRequest(" Ten Moi ", "moi@x.vn", null, List.of("MANAGER")), 1, "ip");

        assertEquals("Ten Moi", res.fullName());
        assertEquals(List.of("MANAGER"), res.roles());
        verify(userRoleRepository).deleteAll(List.of(oldRole));
        ArgumentCaptor<UserRole> added = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(added.capture());
        assertEquals(new UserRoleId(7, 2), added.getValue().getId());
        verify(activityLogService).log(eq(1), eq("UPDATE_STAFF"), anyString(), eq("ip"));
    }

    @Test
    void updateWithoutRolesKeepsExistingRoles() {
        isStaffWithRoles(7, "ACTIVE", "CASHIER");

        StaffResponse res = service.update(7, new UpdateStaffRequest("Ten", "moi@x.vn", null, null), 1, "ip");

        assertEquals(List.of("CASHIER"), res.roles());
        verify(userRoleRepository, never()).deleteAll(any());
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    void updateRejectsEmailOrPhoneOfAnotherUser() {
        isStaffWithRoles(7, "ACTIVE", "CASHIER");
        when(userRepository.existsByEmailAndUserIdNot("trung@x.vn", 7)).thenReturn(true);

        BusinessException ex = fails(() -> service.update(7,
                new UpdateStaffRequest("A", "trung@x.vn", null, null), 1, "ip"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateStillRequiresEmailOrPhone() {
        isStaffWithRoles(7, "ACTIVE", "CASHIER");

        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.update(7,
                new UpdateStaffRequest("A", null, "  ", null), 1, "ip")).getStatus());
    }

    // ---------------- UC9: khoa / mo khoa ----------------

    @Test
    void locksAndUnlocksStaffAndLogsBoth() {
        isStaffWithRoles(7, "ACTIVE", "MANAGER");
        StaffResponse locked = service.setStatus(7, "LOCKED", 1, "ip");
        assertEquals("LOCKED", locked.status());
        verify(activityLogService).log(eq(1), eq("LOCK_USER"), anyString(), eq("ip"));

        service.setStatus(7, "ACTIVE", 1, "ip");
        verify(activityLogService).log(eq(1), eq("UNLOCK_USER"), anyString(), eq("ip"));
    }

    /** Review Focus 3: chu tro khong tu khoa minh, du chu tro khong nam trong danh sach nhan vien. */
    @Test
    void cannotChangeOwnStatus() {
        BusinessException ex = fails(() -> service.setStatus(1, "LOCKED", 1, "ip"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsUnknownStatus() {
        for (String bad : List.of("INACTIVE", "x", "", "locked")) {
            assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.setStatus(7, bad, 1, "ip")).getStatus(), bad);
        }
    }

    /** Review Focus 3: chi thao tac duoc tren nhan vien that su (khong phai chu tro, nguoi thue, hay khong ton tai). */
    @Test
    void cannotTouchLandlordTenantOrUnknownUsers() {
        isStaffWithRoles(2, "ACTIVE", "LANDLORD");
        isStaffWithRoles(3, "ACTIVE", "TENANT");
        isStaffWithRoles(4, "ACTIVE", "MANAGER", "TENANT");
        isStaffWithRoles(5, "ACTIVE");
        when(userRepository.findById(99)).thenReturn(Optional.empty());

        for (int id : new int[]{2, 3, 4, 5, 99}) {
            assertEquals(HttpStatus.NOT_FOUND, fails(() -> service.setStatus(id, "LOCKED", 1, "ip")).getStatus(), "id " + id);
            assertEquals(HttpStatus.NOT_FOUND, fails(() -> service.get(id)).getStatus(), "get " + id);
        }
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------------- tim kiem ----------------

    @Test
    @SuppressWarnings("unchecked")
    void searchClampsPagingAndEscapesKeyword() {
        when(userRepository.searchStaff(anyString(), anyString(), any(Collection.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.search("50%_x\\", null, -3, 1000);

        ArgumentCaptor<String> kw = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Collection<String>> roles = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchStaff(kw.capture(), status.capture(), roles.capture(), pageable.capture());
        assertEquals("%50\\%\\_x\\\\%", kw.getValue());
        assertEquals("", status.getValue());
        assertEquals(Set.of("MANAGER", "CASHIER", "METER_READER"), Set.copyOf(roles.getValue()));
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
        verify(userRoleRepository, never()).findRolesByUserIds(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchWithoutKeywordMatchesEverythingAndMapsRoles() {
        when(userRepository.searchStaff(eq("%"), eq("LOCKED"), any(Collection.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user(7, "LOCKED"), user(8, "LOCKED")), PageRequest.of(0, 20), 2));
        when(userRoleRepository.findRolesByUserIds(any(Collection.class))).thenReturn(List.of(
                new Object[]{7, "MANAGER"}, new Object[]{8, "CASHIER"}, new Object[]{8, "METER_READER"}));

        PageResponse<StaffResponse> page = service.search(null, "LOCKED", 0, 0);

        assertEquals(2, page.getTotalElements());
        assertEquals(List.of("MANAGER"), page.getItems().get(0).roles());
        assertEquals(List.of("CASHIER", "METER_READER"), page.getItems().get(1).roles());
    }

    @Test
    void searchRejectsUnknownStatus() {
        assertEquals(HttpStatus.BAD_REQUEST, fails(() -> service.search("a", "xyz", 0, 20)).getStatus());
    }
}
