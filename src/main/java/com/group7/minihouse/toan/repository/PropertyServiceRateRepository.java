package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.PropertyServiceRate;

public interface PropertyServiceRateRepository extends JpaRepository<PropertyServiceRate, Integer> {

    List<PropertyServiceRate> findByPropertyPropertyIdOrderByServiceName(Integer propertyId);

    List<PropertyServiceRate> findByPropertyPropertyIdAndIsActive(Integer propertyId, Boolean isActive);
}
