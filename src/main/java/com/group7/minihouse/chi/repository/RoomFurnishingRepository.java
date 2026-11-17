package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.RoomFurnishing;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomFurnishingRepository extends JpaRepository<RoomFurnishing, Integer> {
    
    @EntityGraph(attributePaths = {"room"})
    List<RoomFurnishing> findByRoom_RoomId(Integer roomId);
}
