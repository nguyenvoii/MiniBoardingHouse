package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.DebtProcessingLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtProcessingLogRepository extends JpaRepository<DebtProcessingLog, Integer> {

    List<DebtProcessingLog> findByDebt_DebtIdOrderByRecordedAtDesc(Integer debtId);

    Optional<DebtProcessingLog> findFirstByDebt_DebtIdOrderByRecordedAtDesc(Integer debtId);
}
