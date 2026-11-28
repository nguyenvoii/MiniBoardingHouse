package com.group7.minihouse.shared.notification;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RoutingNotificationSender implements NotificationSender {

    private final ObjectProvider<SmtpNotificationSender> smtp;
    private final ObjectProvider<SmsSender> sms; // SpeedSMS hoac Twilio, cai nao dang bat
    private final LogNotificationSender fallback;

    public RoutingNotificationSender(ObjectProvider<SmtpNotificationSender> smtp,
                                     ObjectProvider<SmsSender> sms, LogNotificationSender fallback) {
        this.smtp = smtp;
        this.sms = sms;
        this.fallback = fallback;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (recipient.contains("@")) {
            var sender = smtp.getIfAvailable();
            if (sender != null) {
                sender.send(recipient, subject, body);
                return;
            }
        } else {
            var sender = sms.orderedStream().findFirst().orElse(null);
            if (sender != null) {
                sender.send(recipient, body);
                return;
            }
        }
        fallback.send(recipient, subject, body);
    }

    @Override
    public void sendHtml(String recipient, String subject, String text, String html) {
        var sender = recipient.contains("@") ? smtp.getIfAvailable() : null;
        if (sender != null) {
            sender.sendHtml(recipient, subject, text, html);
        } else {
            send(recipient, subject, text); // SMS / chua bat SMTP: chi can ban chu
        }
    }
}
