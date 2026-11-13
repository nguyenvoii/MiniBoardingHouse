package com.group7.minihouse.shared.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `user_notifications`. */
@DynamicInsert
@Entity
@Table(name = "user_notifications")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_notification_id")
    private Integer userNotificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "notif_template_id")
    private NotificationTemplate notifTemplate;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Integer referenceId;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public UserNotification() {}

    public Integer getUserNotificationId() { return userNotificationId; }
    public void setUserNotificationId(Integer userNotificationId) { this.userNotificationId = userNotificationId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public NotificationTemplate getNotifTemplate() { return notifTemplate; }
    public void setNotifTemplate(NotificationTemplate notifTemplate) { this.notifTemplate = notifTemplate; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Integer getReferenceId() { return referenceId; }
    public void setReferenceId(Integer referenceId) { this.referenceId = referenceId; }

    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
