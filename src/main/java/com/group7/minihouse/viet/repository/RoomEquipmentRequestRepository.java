package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.RoomEquipmentRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomEquipmentRequestRepository extends JpaRepository<RoomEquipmentRequest, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEquipmentRequest r where r.equipmentRequestId = :id")
    Optional<RoomEquipmentRequest> findForUpdate(@Param("id") Integer id);

    @Query("select r from RoomEquipmentRequest r where (:status is null or r.status = :status)"
            + " and (:roomId is null or r.room.roomId = :roomId)")
    Page<RoomEquipmentRequest> search(@Param("status") String status, @Param("roomId") Integer roomId,
                                      Pageable pageable);

    List<RoomEquipmentRequest> findByTenant_TenantIdOrderByCreatedAtDesc(Integer tenantId);
}
