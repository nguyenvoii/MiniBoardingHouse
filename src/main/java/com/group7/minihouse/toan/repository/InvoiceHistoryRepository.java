package com.group7.minihouse.toan.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.group7.minihouse.shared.entity.UserActivityLog;

/** Truy vấn nhật ký thao tác liên quan hóa đơn và giao dịch (UC128). */
public interface InvoiceHistoryRepository extends JpaRepository<UserActivityLog, Integer> {
    @Query("select l from UserActivityLog l join fetch l.user u "
         + "where l.actionType in :actions and l.createdAt >= :from and l.createdAt < :to "
         + "and (:actionType = '' or l.actionType = :actionType) "
         + "and (:keyword = '' or lower(l.description) like lower(concat('%', :keyword, '%')) escape '\\') "
         + "order by l.createdAt desc, l.logId desc")
    List<UserActivityLog> search(@Param("actions") List<String> actions,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 @Param("actionType") String actionType, @Param("keyword") String keyword,
                                 Pageable pageable);
}
