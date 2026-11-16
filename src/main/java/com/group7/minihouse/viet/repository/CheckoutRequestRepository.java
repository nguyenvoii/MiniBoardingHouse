package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.CheckoutRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutRequestRepository extends JpaRepository<CheckoutRequest, Integer> {

    @Query("select c from CheckoutRequest c where (:status is null or c.status = :status)"
            + " and (:contractId is null or c.contract.contractId = :contractId)")
    Page<CheckoutRequest> search(@Param("status") String status, @Param("contractId") Integer contractId,
                                 Pageable pageable);

    boolean existsByContract_ContractIdAndStatusIn(Integer contractId, Collection<String> statuses);

    List<CheckoutRequest> findByContract_Tenant_TenantIdOrderByCreatedAtDesc(Integer tenantId);
}
