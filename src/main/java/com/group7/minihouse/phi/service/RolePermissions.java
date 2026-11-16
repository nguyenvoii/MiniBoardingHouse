package com.group7.minihouse.phi.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UC12: bang quyen chi tiet MAC DINH cua tung vai tro (ma quyen lay tu bang permissions, xem db/seed.sql).
 * Day la bang tra cuu de giao dien biet hien/an chuc nang; viec CHAN API that su van la @PreAuthorize theo vai tro
 * (hasRole / hasAnyRole). Doi quyen cua 1 nguoi = doi vai tro cua ho (UC9, PUT /api/staff/{id}).
 * LANDLORD co moi quyen; MANAGER co moi quyen tru USER_MANAGE; TENANT khong co quyen nhan vien nao.
 */
public final class RolePermissions {

    public static final List<String> ROLES = List.of("LANDLORD", "MANAGER", "CASHIER", "METER_READER", "TENANT");

    private static final Map<String, Set<String>> FIXED = Map.of(
            // Thu ngan: xem phong/khach/hop dong/hoa don, thu tien va cong no
            "CASHIER", Set.of("PROPERTY_VIEW", "ROOM_VIEW", "TENANT_VIEW", "CONTRACT_VIEW", "INVOICE_VIEW",
                    "PAYMENT_MANAGE", "DEBT_MANAGE"),
            // Nhan vien ghi dien nuoc: xem phong va ghi chi so
            "METER_READER", Set.of("PROPERTY_VIEW", "ROOM_VIEW", "METER_VIEW", "METER_WRITE"));

    private RolePermissions() {
    }

    /** Quyen mac dinh cua 1 vai tro; allCodes la toan bo ma quyen trong bang permissions. Vai tro la -> rong. */
    public static Set<String> defaults(String role, Collection<String> allCodes) {
        return switch (role) {
            case "LANDLORD" -> new LinkedHashSet<>(allCodes);
            case "MANAGER" -> {
                Set<String> s = new LinkedHashSet<>(allCodes);
                s.remove("USER_MANAGE");
                yield s;
            }
            default -> {
                Set<String> fixed = FIXED.get(role);
                Set<String> s = new LinkedHashSet<>();
                if (fixed != null) {
                    allCodes.stream().filter(fixed::contains).forEach(s::add);
                }
                yield s;
            }
        };
    }

    /** Hop quyen cua nhieu vai tro (nguoi dung co the giu nhieu vai tro). */
    public static Set<String> effective(Collection<String> roles, Collection<String> allCodes) {
        Set<String> result = new LinkedHashSet<>();
        roles.forEach(r -> result.addAll(defaults(r, allCodes)));
        return result;
    }
}
