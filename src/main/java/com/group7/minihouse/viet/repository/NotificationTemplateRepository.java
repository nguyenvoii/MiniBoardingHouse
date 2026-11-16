package com.group7.minihouse.viet.repository;

import com.group7.minihouse.shared.entity.NotificationTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Integer> {

    @Query("select t from NotificationTemplate t where (:code is null or t.templateCode = :code)"
            + " and (:channel is null or t.channel = :channel) and (:active is null or t.isActive = :active)"
            + " order by t.templateCode, t.channel")
    List<NotificationTemplate> search(@Param("code") String code, @Param("channel") String channel,
                                      @Param("active") Boolean active);

    @Query("select t from NotificationTemplate t where t.templateCode = :code and t.channel = :channel")
    Optional<NotificationTemplate> findByCodeAndChannel(@Param("code") String code, @Param("channel") String channel);

    @Query("select t from NotificationTemplate t where t.templateCode = :code and t.channel = :channel"
            + " and t.isActive = true")
    Optional<NotificationTemplate> findActive(@Param("code") String code, @Param("channel") String channel);
}
