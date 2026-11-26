package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.MaintenanceRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Integer> {

    Optional<MaintenanceRequest> findFirstByIssuePhotoUrl(String issuePhotoUrl);

    @Query("select r from MaintenanceRequest r where (:status is null or r.status = :status)"
            + " and (:priority is null or r.priority = :priority)"
            + " and (:category is null or r.category = :category)"
            + " and (:roomId is null or r.room.roomId = :roomId)")
    Page<MaintenanceRequest> search(@Param("status") String status, @Param("priority") String priority,
                                    @Param("category") String category, @Param("roomId") Integer roomId,
                                    Pageable pageable);

    List<MaintenanceRequest> findByTenant_TenantIdOrderByCreatedAtDesc(Integer tenantId);

    // ---------- UC131: thống kê (khoảng thời gian: from <= created_at < to) ----------

    /** UC131: số sự cố theo trạng thái. */
    @Query("select r.status, count(r) from MaintenanceRequest r"
            + " where r.createdAt >= :from and r.createdAt < :to group by r.status order by count(r) desc")
    List<Object[]> countByStatus(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** UC131: theo nhóm sự cố: số lượng, tổng chi phí, điểm đánh giá trung bình (log mới nhất của mỗi sự cố). */
    @Query("select r.category, count(r), sum(l.repairCost), avg(l.tenantRating) from MaintenanceRequest r"
            + " left join MaintenanceLog l on l.request = r and l.maintenanceLogId ="
            + " (select max(l2.maintenanceLogId) from MaintenanceLog l2 where l2.request = r)"
            + " where r.createdAt >= :from and r.createdAt < :to group by r.category order by count(r) desc")
    List<Object[]> statsByCategory(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** UC131: số sự cố theo tháng tạo (năm, tháng, số lượng). */
    @Query("select year(r.createdAt), month(r.createdAt), count(r) from MaintenanceRequest r"
            + " where r.createdAt >= :from and r.createdAt < :to"
            + " group by year(r.createdAt), month(r.createdAt) order by year(r.createdAt), month(r.createdAt)")
    List<Object[]> countByMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** UC131: phòng hay hỏng nhất; giới hạn số dòng bằng Pageable. */
    @Query("select rm.roomId, rm.roomCode, count(r) from MaintenanceRequest r join r.room rm"
            + " where r.createdAt >= :from and r.createdAt < :to"
            + " group by rm.roomId, rm.roomCode order by count(r) desc, rm.roomCode")
    List<Object[]> topRooms(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /** UC131: cặp (thời điểm tạo, thời điểm xong) của các sự cố đã xử lý xong, để tính thời gian xử lý trung bình. */
    @Query("select r.createdAt, l.completedAt from MaintenanceRequest r"
            + " join MaintenanceLog l on l.request = r and l.maintenanceLogId ="
            + " (select max(l2.maintenanceLogId) from MaintenanceLog l2 where l2.request = r)"
            + " where l.completedAt is not null and r.createdAt >= :from and r.createdAt < :to")
    List<Object[]> resolvedTimes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** UC131: lịch sử bảo trì của một phòng, mới nhất trước. Cột: id, nhóm, mô tả, ưu tiên, trạng thái, ngày tạo, người xử lý, chi phí, điểm, ngày xong. */
    @Query("select r.requestId, r.category, r.description, r.priority, r.status, r.createdAt, s.fullName,"
            + " l.repairCost, l.tenantRating, l.completedAt from MaintenanceRequest r"
            + " left join MaintenanceLog l on l.request = r and l.maintenanceLogId ="
            + " (select max(l2.maintenanceLogId) from MaintenanceLog l2 where l2.request = r)"
            + " left join l.assignedStaff s where r.room.roomId = :roomId order by r.createdAt desc")
    List<Object[]> roomHistory(@Param("roomId") Integer roomId, Pageable pageable);
}