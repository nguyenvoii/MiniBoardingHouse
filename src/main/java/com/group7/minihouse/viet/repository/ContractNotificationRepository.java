package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.ContractNotification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractNotificationRepository extends JpaRepository<ContractNotification, Integer> {

    boolean existsByContract_ContractIdAndNotificationType(Integer contractId, String notificationType);
}
