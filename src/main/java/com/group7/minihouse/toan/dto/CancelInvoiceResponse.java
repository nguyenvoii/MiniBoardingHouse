package com.group7.minihouse.toan.dto;

import java.time.LocalDateTime;

/** UC126: kết quả hủy hóa đơn. */
public record CancelInvoiceResponse(
        Integer invoiceId,
        String invoiceCode,
        String paymentStatus,
        String reason,
        LocalDateTime cancelledAt) {
}
