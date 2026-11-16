package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.RateChangeHistory;

public interface RateChangeHistoryRepository extends JpaRepository<RateChangeHistory, Integer> {

    List<RateChangeHistory> findByPropertyPropertyIdOrderByChangedAtDesc(Integer propertyId);

    List<RateChangeHistory> findByPropertyPropertyIdAndServiceNameOrderByChangedAtDesc(
            Integer propertyId, String serviceName);
}
