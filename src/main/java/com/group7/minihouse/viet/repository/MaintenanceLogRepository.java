package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.MaintenanceLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Integer> {

    Optional<MaintenanceLog> findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(Integer requestId);
}
