package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.RoomCustomRate;

public interface RoomCustomRateRepository extends JpaRepository<RoomCustomRate, Integer> {

    List<RoomCustomRate> findByRoomRoomId(Integer roomId);
}
