package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.entity.UserActivityLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi va xem nhat ky thao tac (bang user_activity_logs).
 * Cac action_type dang dung: LOGIN, LOGOUT, CHANGE_PASSWORD, UPDATE_PROFILE, CREATE_STAFF, UPDATE_STAFF,
 * LOCK_USER, UNLOCK_USER, CREATE_TENANT_ACCOUNT.
 */
@Service
public class ActivityLogService {

    private final UserActivityLogRepository logRepository;
    private final UserRepository userRepository;

    public ActivityLogService(UserActivityLogRepository logRepository, UserRepository userRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
    }

    /** Ghi 1 dong nhat ky cho nguoi thuc hien hanh dong (userId la nguoi thao tac, khong phai doi tuong bi tac dong). */
    @Transactional
    public void log(Integer userId, String actionType, String description, String ip) {
        UserActivityLog log = new UserActivityLog();
        log.setUser(userRepository.getReferenceById(userId));
        log.setActionType(actionType);
        log.setDescription(description);
        log.setIpAddress(ip);
        logRepository.save(log);
    }
}
