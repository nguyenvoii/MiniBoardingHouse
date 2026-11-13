package com.group7.minihouse.shared.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicInsert;

/** Bang `debt_reminders`. */
@DynamicInsert
@Entity
@Table(name = "debt_reminders")
public class DebtReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id")
    private Integer reminderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debt_id", nullable = false)
    private TenantDebt debt;

    @Column(name = "reminder_level", nullable = false, length = 20)
    private String reminderLevel;

    @Column(name = "message_content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String messageContent;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public DebtReminder() {}

    public Integer getReminderId() { return reminderId; }
    public void setReminderId(Integer reminderId) { this.reminderId = reminderId; }

    public TenantDebt getDebt() { return debt; }
    public void setDebt(TenantDebt debt) { this.debt = debt; }

    public String getReminderLevel() { return reminderLevel; }
    public void setReminderLevel(String reminderLevel) { this.reminderLevel = reminderLevel; }

    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

}
