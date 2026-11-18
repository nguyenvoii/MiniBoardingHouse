package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.UserNotification;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Integer> {

    @Query("select n from UserNotification n where n.user.userId = :uid and (:unreadOnly = false or n.isRead = false)")
    Page<UserNotification> search(@Param("uid") Integer userId, @Param("unreadOnly") boolean unreadOnly,
                                  Pageable pageable);

    @Query("select count(n) from UserNotification n where n.user.userId = :uid and n.isRead = false")
    long countUnread(@Param("uid") Integer userId);

    @Query("select n from UserNotification n where n.userNotificationId = :id and n.user.userId = :uid")
    Optional<UserNotification> findOwned(@Param("id") Integer id, @Param("uid") Integer userId);

    @Modifying
    @Query("update UserNotification n set n.isRead = true where n.user.userId = :uid and n.isRead = false")
    int markAllRead(@Param("uid") Integer userId);

    @Modifying
    @Query("delete from UserNotification n where n.user.userId = :uid and n.isRead = true")
    int deleteRead(@Param("uid") Integer userId);

    // ---------- UC130: thông báo chung (reference_type = 'ANNOUNCEMENT', reference_id = mã đợt gửi) ----------

    boolean existsByReferenceTypeAndReferenceId(String referenceType, Integer referenceId);

    boolean existsByReferenceTypeAndTitleAndCreatedAtAfter(String referenceType, String title, LocalDateTime after);

    /** Mỗi dòng một đợt gửi: mã đợt, tiêu đề, thời điểm gửi, tổng người nhận, số người đã đọc. */
    @Query(value = "select n.referenceId, min(n.title), min(n.createdAt), count(n),"
            + " sum(case when n.isRead = true then 1 else 0 end) from UserNotification n"
            + " where n.referenceType = 'ANNOUNCEMENT' group by n.referenceId order by min(n.createdAt) desc",
            countQuery = "select count(distinct n.referenceId) from UserNotification n"
                    + " where n.referenceType = 'ANNOUNCEMENT'")
    Page<Object[]> announcementSummaries(Pageable pageable);

    /** Người chưa đọc xếp trước, để nhân viên thấy ngay ai cần nhắc. */
    @Query("select n from UserNotification n join fetch n.user where n.referenceType = 'ANNOUNCEMENT'"
            + " and n.referenceId = :batchId order by n.isRead, n.user.fullName")
    List<UserNotification> announcementRecipients(@Param("batchId") Integer batchId, Pageable pageable);

    @Query("select count(n) from UserNotification n where n.referenceType = 'ANNOUNCEMENT'"
            + " and n.referenceId = :batchId")
    long countAnnouncement(@Param("batchId") Integer batchId);

    @Query("select count(n) from UserNotification n where n.referenceType = 'ANNOUNCEMENT'"
            + " and n.referenceId = :batchId and n.isRead = true")
    long countAnnouncementRead(@Param("batchId") Integer batchId);
}
