package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.UserActivityLog;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Integer> {

    /** userId = 0: moi nguoi dung; actionType '' : moi hanh dong (tranh tham so null kho xu ly tren SQL Server). */
    @Query(value = "select l from UserActivityLog l join fetch l.user "
            + "where (:userId = 0 or l.user.userId = :userId) "
            + "and (:actionType = '' or l.actionType = :actionType) "
            + "and l.createdAt >= :fromTime and l.createdAt <= :toTime",
            countQuery = "select count(l) from UserActivityLog l "
                    + "where (:userId = 0 or l.user.userId = :userId) "
                    + "and (:actionType = '' or l.actionType = :actionType) "
                    + "and l.createdAt >= :fromTime and l.createdAt <= :toTime")
    Page<UserActivityLog> search(@Param("userId") int userId, @Param("actionType") String actionType,
                                 @Param("fromTime") LocalDateTime fromTime, @Param("toTime") LocalDateTime toTime,
                                 Pageable pageable);
}
