package com.group7.minihouse.shared.notification;

/**
 * Gui thong bao (email/SMS) cho 1 nguoi nhan. Dung chung cho OTP, nhac no, hoa don...
 * Ban mac dinh LogNotificationSender chi ghi ra log (chua gui that); muon gui that thi them 1 @Component
 * khac danh dau @Primary, cac noi goi khong phai sua.
 */
public interface NotificationSender {

    /** recipient la email hoac so dien thoai. */
    void send(String recipient, String subject, String body);

    /** Thu co ban HTML + ban chu thuong. Mac dinh chi gui ban chu (SMS, log); SMTP gui ca hai. */
    default void sendHtml(String recipient, String subject, String text, String html) {
        send(recipient, subject, text);
    }
}
