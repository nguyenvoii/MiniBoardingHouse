package com.group7.minihouse.phi.dto;

import java.time.LocalDateTime;

/** 1 file sao luu: ten, dung luong (byte, null neu app khong doc duoc thu muc), thoi diem sua cuoi. */
public record BackupInfo(String name, Long sizeBytes, LocalDateTime modifiedAt) {
}
