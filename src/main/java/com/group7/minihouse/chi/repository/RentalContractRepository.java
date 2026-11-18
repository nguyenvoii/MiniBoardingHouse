package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("chiRentalContractRepository")
public interface RentalContractRepository extends JpaRepository<RentalContract, Integer> {
    List<RentalContract> findAllByOrderByCreatedAtDesc();
    List<RentalContract> findByRoom_RoomIdOrderByCreatedAtDesc(Integer roomId);
    List<RentalContract> findByTenant_TenantIdOrderByCreatedAtDesc(Integer tenantId);
}
