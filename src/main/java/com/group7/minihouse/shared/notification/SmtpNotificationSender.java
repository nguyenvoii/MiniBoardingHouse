package com.group7.minihouse.shared.notification;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpNotificationSender(JavaMailSender mailSender,
                                  @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        deliver(recipient, subject, body, false);
    }

    public void sendHtml(String recipient, String subject, String body) {
        deliver(recipient, subject, body, true);
    }

    /** Thu 2 phan (chu thuong + HTML), hien ten nguoi gui la Stayly. */
    @Override
    public void sendHtml(String recipient, String subject, String text, String html) {
        var message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Stayly");
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailPreparationException(ex);
        }
        mailSender.send(message);
    }

    private void deliver(String recipient, String subject, String body, boolean html) {
        var message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, html);
        } catch (MessagingException ex) {
            throw new MailPreparationException(ex);
        }
        mailSender.send(message);
    }
}
