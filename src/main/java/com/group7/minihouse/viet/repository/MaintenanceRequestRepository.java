package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.MaintenanceRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Integer> {

    @Query("select r from MaintenanceRequest r where (:status is null or r.status = :status)"
            + " and (:priority is null or r.priority = :priority)"
            + " and (:category is null or r.category = :category)"
            + " and (:roomId is null or r.room.roomId = :roomId)")
    Page<MaintenanceRequest> search(@Param("status") String status, @Param("priority") String priority,
                                    @Param("category") String category, @Param("roomId") Integer roomId,
                                    Pageable pageable);

    List<MaintenanceRequest> findByTenant_TenantIdOrderByCreatedAtDesc(Integer tenantId);
}
