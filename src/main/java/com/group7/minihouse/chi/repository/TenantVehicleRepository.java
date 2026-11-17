package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.TenantVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TenantVehicleRepository extends JpaRepository<TenantVehicle, Integer> {
    List<TenantVehicle> findByTenantTenantId(Integer tenantId);

    boolean existsByLicensePlateIgnoreCase(String licensePlate);
}
