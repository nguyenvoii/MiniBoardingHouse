package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.TenantDebt;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantDebtRepository extends JpaRepository<TenantDebt, Integer> {

    @Query("select d from TenantDebt d where (:status is null or d.debtStatus = :status)"
            + " and (:roomId is null or d.room.roomId = :roomId)"
            + " and (:tenantId is null or d.tenant.tenantId = :tenantId)")
    Page<TenantDebt> search(@Param("status") String status, @Param("roomId") Integer roomId,
                            @Param("tenantId") Integer tenantId, Pageable pageable);

    @Query("select d from TenantDebt d where (:status is null or d.debtStatus = :status)"
            + " order by d.totalOutstandingDebt desc")
    List<TenantDebt> findAllForExport(@Param("status") String status);

    List<TenantDebt> findByTenant_TenantIdOrderByUpdatedAtDesc(Integer tenantId);

    Optional<TenantDebt> findFirstByTenant_TenantIdAndRoom_RoomIdOrderByDebtIdDesc(Integer tenantId, Integer roomId);

    List<TenantDebt> findByDebtStatusNot(String debtStatus);

    List<TenantDebt> findByDebtStatusNotOrderByDebtId(String debtStatus);

    /** Moi dong: [debtStatus, so luong, tong tien con no]. */
    @Query("select d.debtStatus, count(d), coalesce(sum(d.totalOutstandingDebt), 0) from TenantDebt d group by d.debtStatus")
    List<Object[]> summarizeByStatus();
}
