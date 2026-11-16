package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.DebtPenaltyLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtPenaltyLogRepository extends JpaRepository<DebtPenaltyLog, Integer> {

    List<DebtPenaltyLog> findByDebt_DebtIdOrderByCreatedAtDesc(Integer debtId);

    Optional<DebtPenaltyLog> findFirstByDebt_DebtIdOrderByCreatedAtDesc(Integer debtId);
}
