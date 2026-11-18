package com.group7.minihouse.toan.service;

import com.group7.minihouse.shared.entity.User;
import com.group7.minihouse.shared.entity.UserActivityLog;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.repository.ToanUserActivityLogRepository;
import com.group7.minihouse.toan.repository.ToanUserRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** Ghi nhat ky thao tac (user_activity_logs) cho cac duong thu tien / can tru cua Toan. Khong co nguoi dung thi bo qua. */
@Component
public class ToanActivityWriter {

    private final ToanUserRepository userRepo;
    private final ToanUserActivityLogRepository logRepo;

    public ToanActivityWriter(ToanUserRepository userRepo, ToanUserActivityLogRepository logRepo) {
        this.userRepo = userRepo;
        this.logRepo = logRepo;
    }

    public void log(Integer actorUserId, String actionType, String description, String ipAddress) {
        if (actorUserId == null) {
            return;
        }
        User actor = userRepo.findById(actorUserId)
                .orElseThrow(() -> BusinessException.notFound("Tài khoản người thao tác"));
        UserActivityLog log = new UserActivityLog();
        log.setUser(actor);
        log.setActionType(actionType);
        log.setDescription(description);
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        logRepo.save(log);
    }
}
