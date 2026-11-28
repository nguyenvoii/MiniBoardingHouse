package com.group7.minihouse.shared.notification;

/** Kenh gui SMS (Twilio, SpeedSMS...). Tai mot thoi diem chi bat 1 nha cung cap; RoutingNotificationSender chon cai dang bat. */
public interface SmsSender {

    /** recipient la so dien thoai (0901234567, +84901234567 hoac 84901234567); body nen la chu khong dau de vua 1 tin. */
    void send(String recipient, String body);
}
