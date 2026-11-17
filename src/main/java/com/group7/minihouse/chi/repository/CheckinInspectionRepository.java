package com.group7.minihouse.chi.repository;

import com.group7.minihouse.chi.entity.CheckinInspection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckinInspectionRepository extends JpaRepository<CheckinInspection, Integer> {

    @EntityGraph(attributePaths = {"room", "tenant", "contract"})
    List<CheckinInspection> findAllByOrderByCheckinDateDesc();

    @EntityGraph(attributePaths = {"room", "tenant", "contract"})
    List<CheckinInspection> findByRoom_RoomIdOrderByCheckinDateDesc(Integer roomId);

    @EntityGraph(attributePaths = {"room", "tenant", "contract"})
    Optional<CheckinInspection> findById(Integer id);

    boolean existsByContract_ContractId(Integer contractId);
}
