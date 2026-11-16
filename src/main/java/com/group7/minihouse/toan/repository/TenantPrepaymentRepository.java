package com.group7.minihouse.toan.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.TenantPrepayment;

public interface TenantPrepaymentRepository extends JpaRepository<TenantPrepayment, Integer> {

    Optional<TenantPrepayment> findByTenantTenantId(Integer tenantId);
}
