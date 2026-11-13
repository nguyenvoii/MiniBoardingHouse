package com.group7.minihouse.shared.security;

import java.util.List;
import java.util.Optional;

/**
 * Tra cuu trang thai va vai tro HIEN TAI cua tai khoan trong DB, goi moi request co token hop le.
 * Nho do khoa/vo hieu hoa tai khoan va doi vai tro (giang chuc, mo khoa...) co hieu luc NGAY, khong doi token het han:
 * quyen cua request lay tu DB chu khong lay tu token. Cai dat o package phi.
 */
public interface UserStatusChecker {

    /** Vai tro hien tai neu tai khoan dang ACTIVE (co the rong); rong neu bi khoa/ngung/khong ton tai. */
    Optional<List<String>> activeRoles(Integer userId);
}
