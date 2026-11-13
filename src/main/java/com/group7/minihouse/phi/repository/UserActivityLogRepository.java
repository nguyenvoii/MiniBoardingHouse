package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Integer> {
}
