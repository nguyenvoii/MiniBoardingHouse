package com.group7.minihouse.viet.service;

import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.UserNotification;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.NotificationDtos.NotificationResponse;
import com.group7.minihouse.viet.repository.UserNotificationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC123: trung tam thong bao cua nguoi dung (xem, danh dau da doc, xoa).
 * Moi ham deu loc theo userId lay tu token, nguoi nay khong thao tac duoc thong bao cua nguoi khac.
 */
@Service
public class NotificationCenterService {

    private final UserNotificationRepository repository;

    public NotificationCenterService(UserNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Integer userId, boolean unreadOnly, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "userNotificationId"));
        return new PageResponse<>(repository.search(userId, unreadOnly, VietSupport.page(page, size, sort))
                .map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Integer userId) {
        return repository.countUnread(userId);
    }

    @Transactional
    public NotificationResponse markRead(Integer userId, Integer id) {
        UserNotification n = findOwned(userId, id);
        n.setIsRead(true);
        return NotificationResponse.from(repository.save(n));
    }

    @Transactional
    public int markAllRead(Integer userId) {
        return repository.markAllRead(userId);
    }

    @Transactional
    public void delete(Integer userId, Integer id) {
        repository.delete(findOwned(userId, id));
    }

    /** Xoa tat ca thong bao da doc cua chinh minh. */
    @Transactional
    public int deleteRead(Integer userId) {
        return repository.deleteRead(userId);
    }

    private UserNotification findOwned(Integer userId, Integer id) {
        return repository.findOwned(id, userId).orElseThrow(() -> BusinessException.notFound("Thong bao"));
    }
}
