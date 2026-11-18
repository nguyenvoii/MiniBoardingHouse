package com.group7.minihouse.viet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** DTO cho thông báo chung tới người thuê và theo dõi số người đã đọc (UC130). */
public final class AnnouncementDtos {

    private AnnouncementDtos() {
    }

    /**
     * scope = ALL (cả khu) hoặc FLOOR (chỉ các tầng trong floorIds, là floor_id của bảng property_floors).
     * Nội dung hỗ trợ biến {{ten_khach}} và {{phong}}; tiêu đề là văn bản thường.
     */
    public record CreateAnnouncementRequest(
            @NotBlank(message = "không được để trống") @Size(max = 200, message = "tối đa 200 ký tự") String title,
            @NotBlank(message = "không được để trống") @Size(max = 5000, message = "tối đa 5000 ký tự") String content,
            @NotBlank(message = "không được để trống") String scope,
            List<Integer> floorIds) {
    }

    /** Khách có hợp đồng nhưng chưa có tài khoản app, nhân viên cần báo trực tiếp. */
    public record MissingAccount(Integer tenantId, String fullName, String roomCodes) {
    }

    public record AnnouncementSendResult(Integer batchId, int recipients, int sent,
                                         List<MissingAccount> noAccount) {
    }

    public record AnnouncementSummary(Integer batchId, String title, LocalDateTime sentAt, long total, long read) {
    }

    public record AnnouncementRecipient(Integer userId, String fullName, boolean read) {
    }

    /** content là bản gửi cho người đầu danh sách (nội dung có thể khác nhau giữa người nhận nếu dùng biến). */
    public record AnnouncementDetail(Integer batchId, String title, String content, LocalDateTime sentAt,
                                     long total, long read, List<AnnouncementRecipient> recipients) {
    }
}
