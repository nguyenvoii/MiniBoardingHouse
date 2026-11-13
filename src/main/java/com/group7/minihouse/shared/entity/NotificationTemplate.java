package com.group7.minihouse.shared.entity;

import jakarta.persistence.*;

/** Bang `notification_templates`. */
@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notif_template_id")
    private Integer notifTemplateId;

    @Column(name = "template_code", nullable = false, length = 50)
    private String templateCode;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    public NotificationTemplate() {}

    public Integer getNotifTemplateId() { return notifTemplateId; }
    public void setNotifTemplateId(Integer notifTemplateId) { this.notifTemplateId = notifTemplateId; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

}
