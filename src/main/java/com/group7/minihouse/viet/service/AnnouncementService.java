package com.group7.minihouse.viet.service;

import com.group7.minihouse.chi.repository.PropertyFloorRepository;
import com.group7.minihouse.shared.dto.PageResponse;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.UserNotification;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementDetail;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementRecipient;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementSendResult;
import com.group7.minihouse.viet.dto.AnnouncementDtos.AnnouncementSummary;
import com.group7.minihouse.viet.dto.AnnouncementDtos.CreateAnnouncementRequest;
import com.group7.minihouse.viet.dto.AnnouncementDtos.MissingAccount;
import com.group7.minihouse.viet.repository.AnnouncementRecipientRepository;
import com.group7.minihouse.viet.repository.UserNotificationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC130: gửi thông báo chung tới người thuê (cả khu hoặc theo tầng) và theo dõi số người đã đọc.
 * Không dùng bảng mới: mỗi người nhận có một dòng user_notifications với reference_type = 'ANNOUNCEMENT'
 * và reference_id = mã đợt gửi (batchId). Số người đã đọc = số dòng của đợt có is_read = true.
 */
@Service
public class AnnouncementService {

    static final String TYPE = "ANNOUNCEMENT";
    static final Set<String> SCOPES = Set.of("ALL", "FLOOR");
    static final int DUPLICATE_SECONDS = 60;
    static final int MAX_RECIPIENTS = 1000;
    private static final int BATCH_ID_TRIES = 5;

    private final NotificationSender sender;
    private final UserNotificationRepository notifications;
    private final AnnouncementRecipientRepository recipients;
    private final PropertyFloorRepository floors;

    public AnnouncementService(NotificationSender sender, UserNotificationRepository notifications,
                               AnnouncementRecipientRepository recipients, PropertyFloorRepository floors) {
        this.sender = sender;
        this.notifications = notifications;
        this.recipients = recipients;
        this.floors = floors;
    }

    /** UC130: gửi thông báo cho khách có hợp đồng ACTIVE/EXPIRING_SOON (cả khu hoặc theo tầng). */
    @Transactional
    public AnnouncementSendResult send(CreateAnnouncementRequest req) {
        if (req == null) {
            throw new BusinessException("Thiếu nội dung thông báo");
        }
        String title = VietSupport.requireText(req.title(), "tiêu đề");
        String content = VietSupport.requireText(req.content(), "nội dung");
        if (title.length() > 200) {
            throw new BusinessException("Tiêu đề tối đa 200 ký tự");
        }
        String scope = VietSupport.enumOrNull(req.scope(), SCOPES, "scope");
        if (scope == null) {
            throw new BusinessException("Thiếu phạm vi gửi (ALL hoặc FLOOR)");
        }
        List<Object[]> rows = findRecipients(scope, req.floorIds());
        if (rows.isEmpty()) {
            throw new BusinessException("Không có người thuê nào đang có hợp đồng trong phạm vi đã chọn");
        }
        // Chặn bấm gửi hai lần: cùng tiêu đề đã gửi trong 60 giây gần nhất.
        if (notifications.existsByReferenceTypeAndTitleAndCreatedAtAfter(TYPE, title,
                LocalDateTime.now().minusSeconds(DUPLICATE_SECONDS))) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Thông báo cùng tiêu đề vừa được gửi cách đây chưa đến " + DUPLICATE_SECONDS + " giây");
        }

        Integer batchId = newBatchId();
        Map<Integer, Recipient> byTenant = groupByTenant(rows);
        int sent = 0;
        List<MissingAccount> missing = new ArrayList<>();
        for (Recipient r : byTenant.values()) {
            String rooms = String.join(", ", r.roomCodes);
            Map<String, String> params = Map.of("ten_khach", String.valueOf(r.tenant.getFullName()),
                    "phong", rooms);
            if (sender.notifyTenant(r.tenant.getTenantId(), null, title, content, params, TYPE, batchId)) {
                sent++;
            } else {
                missing.add(new MissingAccount(r.tenant.getTenantId(), r.tenant.getFullName(), rooms));
            }
        }
        return new AnnouncementSendResult(batchId, byTenant.size(), sent, missing);
    }

    /** UC130: các đợt đã gửi, mới nhất trước, kèm số người đã đọc. */
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementSummary> list(int page, int size) {
        return new PageResponse<>(notifications
                .announcementSummaries(VietSupport.page(page, size, Sort.unsorted()))
                .map(o -> new AnnouncementSummary((Integer) o[0], (String) o[1], (LocalDateTime) o[2],
                        ((Number) o[3]).longValue(), o[4] == null ? 0 : ((Number) o[4]).longValue())));
    }

    /** UC130: ai đã đọc, ai chưa đọc của một đợt gửi (tối đa 1000 người). */
    @Transactional(readOnly = true)
    public AnnouncementDetail detail(Integer batchId) {
        List<UserNotification> list = notifications.announcementRecipients(batchId,
                PageRequest.of(0, MAX_RECIPIENTS));
        if (list.isEmpty()) {
            throw BusinessException.notFound("Đợt thông báo");
        }
        UserNotification first = list.get(0);
        LocalDateTime sentAt = list.stream().map(UserNotification::getCreatedAt)
                .min(LocalDateTime::compareTo).orElse(null);
        List<AnnouncementRecipient> people = list.stream()
                .map(n -> new AnnouncementRecipient(n.getUser().getUserId(), n.getUser().getFullName(),
                        Boolean.TRUE.equals(n.getIsRead())))
                .toList();
        return new AnnouncementDetail(batchId, first.getTitle(), first.getContent(), sentAt,
                notifications.countAnnouncement(batchId), notifications.countAnnouncementRead(batchId), people);
    }

    private List<Object[]> findRecipients(String scope, List<Integer> floorIds) {
        if ("ALL".equals(scope)) {
            return recipients.findAllRecipients();
        }
        if (floorIds == null || floorIds.isEmpty()) {
            throw new BusinessException("Gửi theo tầng cần chọn ít nhất một tầng (floorIds)");
        }
        Set<Integer> ids = new LinkedHashSet<>(floorIds);
        if (ids.contains(null)) {
            throw new BusinessException("Danh sách tầng có giá trị trống");
        }
        if (floors.findAllById(ids).size() != ids.size()) {
            throw new BusinessException("Có tầng không tồn tại trong khu trọ");
        }
        return recipients.findRecipientsByFloors(ids);
    }

    /**
     * Mã đợt gửi ngẫu nhiên, kiểm tra chưa dùng. Không dùng "đếm + 1" hay giờ hiện tại để hai đợt gửi
     * cùng lúc không bị gộp thống kê.
     */
    private Integer newBatchId() {
        for (int i = 0; i < BATCH_ID_TRIES; i++) {
            int candidate = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            if (!notifications.existsByReferenceTypeAndReferenceId(TYPE, candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(HttpStatus.CONFLICT, "Không tạo được mã đợt gửi, vui lòng thử lại");
    }

    /** Khách thuê nhiều phòng chỉ nhận một thông báo, ghi đủ mã các phòng. */
    private static Map<Integer, Recipient> groupByTenant(List<Object[]> rows) {
        Map<Integer, Recipient> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Tenant t = (Tenant) row[0];
            map.computeIfAbsent(t.getTenantId(), k -> new Recipient(t)).roomCodes.add((String) row[1]);
        }
        return map;
    }

    private static final class Recipient {
        private final Tenant tenant;
        private final List<String> roomCodes = new ArrayList<>();

        private Recipient(Tenant tenant) {
            this.tenant = tenant;
        }
    }
}
