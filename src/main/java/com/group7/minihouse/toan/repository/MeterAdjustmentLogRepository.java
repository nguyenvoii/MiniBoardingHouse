package com.group7.minihouse.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.entity.MeterAdjustmentLog;

public interface MeterAdjustmentLogRepository extends JpaRepository<MeterAdjustmentLog, Integer> {

    List<MeterAdjustmentLog> findByReadingReadingIdOrderByAdjustedAtDesc(Integer readingId);
}
