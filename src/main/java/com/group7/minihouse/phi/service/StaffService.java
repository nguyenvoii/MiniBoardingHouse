package com.group7.minihouse.phi.service;

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
import com.group7.minihouse.shared.util.PasswordGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC8 them nhan vien, UC9 sua/khoa/mo khoa nhan vien. Chi thao tac tren tai khoan nhan vien that su:
 * co it nhat 1 vai tro MANAGER/CASHIER/METER_READER va khong phai LANDLORD/TENANT.
 * Phan quyen goi API (chi LANDLORD...) nam o controller (@PreAuthorize).
 */
@Service
public class StaffService {

    static final Set<String> STAFF_ROLES = Set.of("MANAGER", "CASHIER", "METER_READER");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "LOCKED", "INACTIVE");
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9]{9,15}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;

    public StaffService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                        RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                        ActivityLogService activityLogService) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
    }

    // ---------------------------------------------------------------- UC8

    @Transactional
    public StaffCreatedResponse create(CreateStaffRequest req, Integer actorId, String ip) {
        Contact c = validateContact(req.fullName(), req.email(), req.phoneNumber());
        List<String> roleCodes = normalizeRoles(req.roles());

        if (c.email() != null && userRepository.existsByEmail(c.email())) {
            throw conflict("Email da duoc tai khoan khac su dung");
        }
        if (c.phone() != null && userRepository.existsByPhoneNumber(c.phone())) {
            throw conflict("So dien thoai da duoc tai khoan khac su dung");
        }
        List<Role> roles = loadRoles(roleCodes);

        String tempPassword = PasswordGenerator.generate();
        User user = new User();
        user.setFullName(c.name());
        user.setEmail(c.email());
        user.setPhoneNumber(c.phone());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setStatus("ACTIVE");
        User saved = userRepository.save(user);
        for (Role role : roles) {
            userRoleRepository.save(newUserRole(saved, role));
        }

        audit(actorId, "CREATE_STAFF", "Them nhan vien " + saved.getUserId() + " (" + String.join(",", roleCodes) + ")", ip);
        return new StaffCreatedResponse(toResponse(saved, roleCodes), tempPassword);
    }

    // ---------------------------------------------------------------- UC9

    @Transactional(readOnly = true)
    public StaffResponse get(Integer id) {
        StaffRef ref = staff(id);
        return toResponse(ref.user(), ref.roles());
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffResponse> search(String keyword, String status, int page, int size) {
        String st = status == null ? "" : status.trim();
        if (!st.isEmpty() && !STATUSES.contains(st)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Trang thai khong hop le (ACTIVE, LOCKED, INACTIVE)");
        }
        int s = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), s, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<User> result = userRepository.searchStaff(likePattern(keyword), st, STAFF_ROLES, pageable);
        Map<Integer, List<String>> rolesByUser = rolesOf(result.getContent());
        return new PageResponse<>(result.map(u -> toResponse(u, rolesByUser.getOrDefault(u.getUserId(), List.of()))));
    }

    @Transactional
    public StaffResponse update(Integer id, UpdateStaffRequest req, Integer actorId, String ip) {
        StaffRef ref = staff(id);
        Contact c = validateContact(req.fullName(), req.email(), req.phoneNumber());
        if (c.email() != null && userRepository.existsByEmailAndUserIdNot(c.email(), id)) {
            throw conflict("Email da duoc tai khoan khac su dung");
        }
        if (c.phone() != null && userRepository.existsByPhoneNumberAndUserIdNot(c.phone(), id)) {
            throw conflict("So dien thoai da duoc tai khoan khac su dung");
        }

        List<String> roleCodes = ref.roles();
        List<Role> newRoles = null;
        if (req.roles() != null) {
            roleCodes = normalizeRoles(req.roles());
            newRoles = loadRoles(roleCodes);
        }

        User user = ref.user();
        user.setFullName(c.name());
        user.setEmail(c.email());
        user.setPhoneNumber(c.phone());
        userRepository.save(user);
        if (newRoles != null) {
            replaceStaffRoles(user, newRoles, roleCodes);
        }

        audit(actorId, "UPDATE_STAFF", "Cap nhat nhan vien " + id, ip);
        return toResponse(user, roleCodes);
    }

    @Transactional
    public StaffResponse setStatus(Integer id, String status, Integer actorId, String ip) {
        if (!"ACTIVE".equals(status) && !"LOCKED".equals(status)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Trang thai chi nhan ACTIVE (mo khoa) hoac LOCKED (khoa)");
        }
        if (id.equals(actorId)) {
            throw conflict("Khong the khoa hoac mo khoa chinh tai khoan cua minh");
        }
        StaffRef ref = staff(id);
        User user = ref.user();
        user.setStatus(status);
        userRepository.save(user);

        boolean lock = "LOCKED".equals(status);
        audit(actorId, lock ? "LOCK_USER" : "UNLOCK_USER", (lock ? "Khoa" : "Mo khoa") + " tai khoan nhan vien " + id, ip);
        return toResponse(user, ref.roles());
    }

    // ---------------------------------------------------------------- noi bo

    private record StaffRef(User user, List<String> roles) {
    }

    private record Contact(String name, String email, String phone) {
    }

    /** Nhan vien that su: ton tai, co vai tro nhan vien, khong phai LANDLORD/TENANT; nguoc lai 404. */
    private StaffRef staff(Integer id) {
        User user = userRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Nhan vien"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(id);
        boolean isStaff = roles.stream().anyMatch(STAFF_ROLES::contains)
                && !roles.contains("LANDLORD") && !roles.contains("TENANT");
        if (!isStaff) {
            throw BusinessException.notFound("Nhan vien");
        }
        return new StaffRef(user, roles);
    }

    private Contact validateContact(String fullName, String email, String phone) {
        String name = fullName == null ? "" : fullName.trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Ho ten khong duoc de trong va toi da 100 ky tu");
        }
        String e = blankToNull(email);
        String p = blankToNull(phone);
        if (e == null && p == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phai co email hoac so dien thoai de dang nhap");
        }
        if (e != null && (e.length() > 255 || !EMAIL.matcher(e).matches())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email khong hop le");
        }
        if (p != null && !PHONE.matcher(p).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "So dien thoai chi gom 9-15 chu so (co the bat dau bang +), khong co dau cach");
        }
        return new Contact(name, e, p);
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phai chon it nhat 1 vai tro nhan vien");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String r : roles) {
            if (r == null || !STAFF_ROLES.contains(r)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "Vai tro khong hop le: " + r + " (chi MANAGER, CASHIER, METER_READER)");
            }
            codes.add(r);
        }
        return new ArrayList<>(codes);
    }

    private List<Role> loadRoles(List<String> codes) {
        List<Role> roles = new ArrayList<>();
        for (String code : codes) {
            roles.add(roleRepository.findByRoleCode(code).orElseThrow(() ->
                    new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Chua nap role " + code + ", chay db/seed.sql")));
        }
        return roles;
    }

    private void replaceStaffRoles(User user, List<Role> newRoles, List<String> newCodes) {
        List<UserRole> current = userRoleRepository.findByUserUserId(user.getUserId());
        List<UserRole> toRemove = current.stream()
                .filter(ur -> STAFF_ROLES.contains(ur.getRole().getRoleCode())
                        && !newCodes.contains(ur.getRole().getRoleCode()))
                .toList();
        if (!toRemove.isEmpty()) {
            userRoleRepository.deleteAll(toRemove);
        }
        Set<String> existing = new LinkedHashSet<>();
        current.forEach(ur -> existing.add(ur.getRole().getRoleCode()));
        for (Role role : newRoles) {
            if (!existing.contains(role.getRoleCode())) {
                userRoleRepository.save(newUserRole(user, role));
            }
        }
    }

    private static UserRole newUserRole(User user, Role role) {
        UserRole ur = new UserRole();
        ur.setId(new UserRoleId(user.getUserId(), role.getRoleId()));
        ur.setUser(user);
        ur.setRole(role);
        return ur;
    }

    private Map<Integer, List<String>> rolesOf(List<User> users) {
        Map<Integer, List<String>> map = new LinkedHashMap<>();
        if (users.isEmpty()) {
            return map;
        }
        List<Integer> ids = users.stream().map(User::getUserId).toList();
        for (Object[] row : userRoleRepository.findRolesByUserIds(ids)) {
            map.computeIfAbsent(((Number) row[0]).intValue(), k -> new ArrayList<>()).add((String) row[1]);
        }
        return map;
    }

    /** Mau LIKE chong ky tu dac biet: '\', '%', '_' duoc escape bang '\'; rong = khop tat ca. */
    private static String likePattern(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        if (k.isEmpty()) {
            return "%";
        }
        String escaped = k.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private void audit(Integer actorId, String action, String description, String ip) {
        if (actorId != null) {
            activityLogService.log(actorId, action, description, ip);
        }
    }

    private static StaffResponse toResponse(User u, List<String> roles) {
        return new StaffResponse(u.getUserId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getStatus(),
                roles, u.getCreatedAt());
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
