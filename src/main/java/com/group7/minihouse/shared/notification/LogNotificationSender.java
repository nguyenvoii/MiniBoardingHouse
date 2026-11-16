package com.group7.minihouse.shared.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ban dev: khong gui that, chi in noi dung thong bao ra log de lay ma OTP khi test/demo. */
@Component
public class LogNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[THONG BAO GIA LAP] den={} | {} | {}", recipient, subject, body);
    }
}
