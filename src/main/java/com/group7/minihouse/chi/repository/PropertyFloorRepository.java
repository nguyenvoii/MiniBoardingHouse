package com.group7.minihouse.chi.repository;

import com.group7.minihouse.shared.entity.PropertyFloor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyFloorRepository extends JpaRepository<PropertyFloor, Integer> {
    List<PropertyFloor> findByProperty_PropertyIdOrderByFloorNumberAsc(Integer propertyId);
    boolean existsByProperty_PropertyIdAndFloorNumber(Integer propertyId, Integer floorNumber);
    boolean existsByProperty_PropertyIdAndFloorNumberAndFloorIdNot(Integer propertyId, Integer floorNumber, Integer floorId);
}
