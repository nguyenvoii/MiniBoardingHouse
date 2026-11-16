package com.group7.minihouse.phi.repository;

import com.group7.minihouse.shared.entity.UserVerification;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVerificationRepository extends JpaRepository<UserVerification, Integer> {

    /** Ma con dung duoc: chua dung (is_verified = 0) va chua het han. */
    @Query("select v from UserVerification v where v.user.userId = :userId and v.type = :type "
            + "and v.isVerified = false and v.expiresAt > :now")
    List<UserVerification> findActive(@Param("userId") Integer userId, @Param("type") String type,
                                      @Param("now") LocalDateTime now);

    /** Xoa ma het han truoc moc cutoff, tra ve so dong da xoa. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("delete from UserVerification v where v.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * So ma da cap (ke ca ma da dung/bi huy) co han sau minExpiresAt, tuc la duoc cap sau thoi diem
     * (minExpiresAt - han ma). Dung de chan xin ma lien tuc.
     */
    @Query("select count(v) from UserVerification v where v.user.userId = :userId and v.type = :type "
            + "and v.expiresAt > :minExpiresAt")
    long countIssuedAfter(@Param("userId") Integer userId, @Param("type") String type,
                          @Param("minExpiresAt") LocalDateTime minExpiresAt);
}
