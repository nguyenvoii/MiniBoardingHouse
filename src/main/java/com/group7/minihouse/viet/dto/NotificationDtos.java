package com.group7.minihouse.viet.dto;

import com.group7.minihouse.shared.entity.NotificationTemplate;
import com.group7.minihouse.shared.entity.UserNotification;
import java.time.LocalDateTime;

/** DTO cho mau thong bao (UC121) va trung tam thong bao (UC123). */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record TemplateRequest(String templateCode, String channel, String subject, String content) {
    }

    public record TemplateResponse(Integer notifTemplateId, String templateCode, String channel, String subject,
                                   String content, Boolean isActive) {
        public static TemplateResponse from(NotificationTemplate t) {
            return new TemplateResponse(t.getNotifTemplateId(), t.getTemplateCode(), t.getChannel(), t.getSubject(),
                    t.getContent(), t.getIsActive());
        }
    }

    /** Ket qua xem truoc sau khi thay cac bien {{ten_bien}} bang gia tri mau. */
    public record TemplatePreview(String subject, String content) {
    }

    public record NotificationResponse(Integer userNotificationId, String title, String content,
                                       String referenceType, Integer referenceId, Boolean isRead,
                                       LocalDateTime createdAt) {
        public static NotificationResponse from(UserNotification n) {
            return new NotificationResponse(n.getUserNotificationId(), n.getTitle(), n.getContent(),
                    n.getReferenceType(), n.getReferenceId(), n.getIsRead(), n.getCreatedAt());
        }
    }

    public record UnreadCount(long unread) {
    }

    public record AffectedCount(int affected) {
    }
}
