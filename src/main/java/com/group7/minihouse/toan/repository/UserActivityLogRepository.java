package com.group7.minihouse.toan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.group7.minihouse.shared.entity.UserActivityLog;

/** Truy cập nhật ký thao tác dùng chung cho các use case thuộc package toan. */
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Integer> {
}
