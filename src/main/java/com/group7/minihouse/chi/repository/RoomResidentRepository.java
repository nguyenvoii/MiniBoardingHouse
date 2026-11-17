package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.RoomResident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomResidentRepository extends JpaRepository<RoomResident, Integer> {
    List<RoomResident> findByRoomRoomId(Integer roomId);
}
