package com.group7.minihouse.shared.security;

/**
 * Kiem tra tai khoan con hoat dong khong (users.status = ACTIVE), goi moi request co token hop le
 * de khoa/vo hieu hoa tai khoan co hieu luc NGAY, khong doi token het han. Cai dat o package phi.
 */
public interface UserStatusChecker {

    /** false neu tai khoan bi khoa, ngung hoat dong hoac khong con ton tai. */
    boolean isActive(Integer userId);
}
