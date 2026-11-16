package com.group7.minihouse.toan.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.MeterAdjustmentLog;

public interface MeterAdjustmentLogRepository extends JpaRepository<MeterAdjustmentLog, Integer> {

    List<MeterAdjustmentLog> findByReadingReadingIdOrderByAdjustedAtDesc(Integer readingId);
}
