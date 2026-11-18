package com.group7.minihouse.toan.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.group7.minihouse.shared.entity.TenantPrepayment;

public interface TenantPrepaymentRepository extends JpaRepository<TenantPrepayment, Integer> {

    Optional<TenantPrepayment> findByTenantTenantId(Integer tenantId);

    /** Khóa số dư khi cấn trừ/hoàn tiền để tránh hai yêu cầu đồng thời dùng cùng một khoản dư. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TenantPrepayment p where p.tenant.tenantId = :tenantId")
    Optional<TenantPrepayment> findByTenantIdForUpdate(@Param("tenantId") Integer tenantId);
}
