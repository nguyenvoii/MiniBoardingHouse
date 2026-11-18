package com.group7.minihouse.toan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** UC126: thông tin yêu cầu hủy hóa đơn phát hành nhầm. */
public record CancelInvoiceRequest(
        @NotBlank(message = "Vui lòng nhập lý do hủy hóa đơn")
        @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
        String reason) {
}
