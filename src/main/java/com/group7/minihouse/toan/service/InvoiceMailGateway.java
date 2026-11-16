package com.group7.minihouse.toan.service;

/**
 * Cong gui email cho hoa don (UC 80). Phan gui email that (SMTP, SendGrid...) thuoc util cua Chi:
 * chi can tao 1 bean implements interface nay, InvoiceService se tu dung. Neu chua co bean,
 * thong bao email duoc luu voi trang thai PENDING.
 */
public interface InvoiceMailGateway {

    /** Gui email; nem exception neu that bai. */
    void send(String toEmail, String subject, String htmlBody);
}
