package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.UserNotification;
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
}
