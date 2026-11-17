package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.RoomMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomMediaRepository extends JpaRepository<RoomMedia, Integer> {
    List<RoomMedia> findByRoomRoomId(Integer roomId);
    List<RoomMedia> findByRoomRoomIdAndIsCoverTrue(Integer roomId);
}
