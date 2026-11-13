package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.ActivityLogResponse;
import com.group7.minihouse.phi.repository.UserActivityLogRepository;
import com.group7.minihouse.phi.repository.UserRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.shared.exception.BusinessException;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi va xem nhat ky thao tac (bang user_activity_logs).
 * Cac action_type dang dung: LOGIN, LOGOUT, CHANGE_PASSWORD, UPDATE_PROFILE, CREATE_STAFF, UPDATE_STAFF,
 * LOCK_USER, UNLOCK_USER, CREATE_TENANT_ACCOUNT.
 */
@Service
public class ActivityLogService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserActivityLogRepository logRepository;
    private final UserRepository userRepository;

    public ActivityLogService(UserActivityLogRepository logRepository, UserRepository userRepository) {
        this.logRepository = logRepository;
        this.userRepository = userRepository;
    }

    /** UC13: tim nhat ky (null = khong loc). Chi goi tu API cua LANDLORD; nguoi dung thuong dung mine(). */
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> search(Integer userId, String actionType, LocalDateTime from,
                                                    LocalDateTime to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Thoi diem bat dau phai truoc thoi diem ket thuc");
        }
        int s = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), s,
                Sort.by(Sort.Direction.DESC, "createdAt", "logId"));
        Page<UserActivityLog> result = logRepository.search(
                userId == null ? 0 : userId,
                actionType == null ? "" : actionType.trim(),
                from == null ? LocalDateTime.of(1900, 1, 1, 0, 0) : from,
                to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to,
                pageable);
        return new PageResponse<>(result.map(l -> new ActivityLogResponse(l.getLogId(), l.getUser().getUserId(),
                l.getUser().getFullName(), l.getActionType(), l.getDescription(), l.getIpAddress(), l.getCreatedAt())));
    }

    /** Nhat ky cua chinh nguoi dung hien tai. */
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> mine(Integer userId, int page, int size) {
        return search(userId, null, null, null, page, size);
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
