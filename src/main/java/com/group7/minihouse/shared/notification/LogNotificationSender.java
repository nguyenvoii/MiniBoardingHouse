package com.group7.minihouse.shared.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ban dev: khong gui that, chi in thong bao ra log de lay ma OTP khi test/demo.
 * app.notification.log-otp=true (MAC DINH, de chay local lay duoc ma OTP/mat khau tam): in ca noi dung o muc INFO.
 * Dat false khi chay that: chi in nguoi nhan + tieu de o INFO, bo noi dung co ma OTP.
 */
@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    private final boolean logBody;

    public LogNotificationSender(@Value("${app.notification.log-otp:true}") boolean logBody) {
        this.logBody = logBody;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (logBody) {
            log.info("[THONG BAO GIA LAP] den={} | {} | {}", recipient, subject, body);
        } else {
            log.info("[THONG BAO GIA LAP] den={} | {} |", recipient, subject);
        }
    }
}
