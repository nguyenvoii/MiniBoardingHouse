package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.RoomAddonFee;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("chiRoomAddonFeeRepository")
public interface RoomAddonFeeRepository extends JpaRepository<RoomAddonFee, Integer> {

    @EntityGraph(attributePaths = {"room", "equipmentRequest"})
    List<RoomAddonFee> findAllByOrderByStartDateDesc();

    @EntityGraph(attributePaths = {"room", "equipmentRequest"})
    List<RoomAddonFee> findByRoom_RoomIdOrderByStartDateDesc(Integer roomId);

    @EntityGraph(attributePaths = {"room", "equipmentRequest"})
    List<RoomAddonFee> findByIsActiveOrderByStartDateDesc(Boolean isActive);
}
