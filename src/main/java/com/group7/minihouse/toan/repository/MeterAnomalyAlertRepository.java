package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.MeterAnomalyAlert;

public interface MeterAnomalyAlertRepository extends JpaRepository<MeterAnomalyAlert, Integer> {

    List<MeterAnomalyAlert> findByReadingReadingId(Integer readingId);

    void deleteByReadingReadingIdAndIsResolved(Integer readingId, Boolean isResolved);

}
