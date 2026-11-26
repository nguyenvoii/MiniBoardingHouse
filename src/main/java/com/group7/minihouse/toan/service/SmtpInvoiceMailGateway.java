package com.group7.minihouse.toan.service;

import com.group7.minihouse.shared.notification.SmtpNotificationSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpInvoiceMailGateway implements InvoiceMailGateway {

    private final SmtpNotificationSender sender;

    public SmtpInvoiceMailGateway(SmtpNotificationSender sender) {
        this.sender = sender;
    }

    @Override
    public void send(String toEmail, String subject, String htmlBody) {
        sender.sendHtml(toEmail, subject, htmlBody);
    }
}
