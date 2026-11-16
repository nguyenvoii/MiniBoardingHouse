package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.DebtReminder;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtReminderRepository extends JpaRepository<DebtReminder, Integer> {

    List<DebtReminder> findByDebt_DebtIdOrderBySentAtDesc(Integer debtId);

    boolean existsByDebt_DebtIdAndReminderLevelAndSentAtAfter(Integer debtId, String reminderLevel, LocalDateTime since);
}
