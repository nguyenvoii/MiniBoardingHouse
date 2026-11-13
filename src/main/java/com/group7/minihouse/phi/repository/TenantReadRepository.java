package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * CHI DOC. Tam thoi dung de noi tai khoan nguoi thue voi ho so khach thue theo so dien thoai.
 * Khi Chi lam xong TenantService thi doi sang goi service do va bo repository nay.
 */
public interface TenantReadRepository extends JpaRepository<Tenant, Integer> {

    /** So khop sau khi cat khoang trang 2 dau (SDT trong tenants co the bi nhap thua dau cach). */
    @Query("select t from Tenant t where trim(t.phoneNumber) = :phone")
    Optional<Tenant> findByPhone(@Param("phone") String phone);
}
