package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.RoomAddonFee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomAddonFeeRepository extends JpaRepository<RoomAddonFee, Integer> {

    @Query("select f from RoomAddonFee f where (:roomId is null or f.room.roomId = :roomId)"
            + " and (:active is null or f.isActive = :active) order by f.startDate desc")
    List<RoomAddonFee> search(@Param("roomId") Integer roomId, @Param("active") Boolean active);

    List<RoomAddonFee> findByEquipmentRequest_EquipmentRequestId(Integer equipmentRequestId);
}
