package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.Room;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Integer> {

    @EntityGraph(attributePaths = {"property", "floor"})
    List<Room> findAll();

    @EntityGraph(attributePaths = {"property", "floor"})
    Optional<Room> findById(Integer id);

    @EntityGraph(attributePaths = {"property", "floor"})
    List<Room> findByFloor_FloorId(Integer floorId);

    List<Room> findByProperty_PropertyId(Integer propertyId);
    List<Room> findByStatus(String status);
    Optional<Room> findByProperty_PropertyIdAndRoomCode(Integer propertyId, String roomCode);
    boolean existsByProperty_PropertyIdAndRoomCode(Integer propertyId, String roomCode);
}
