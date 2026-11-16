package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.repository.UserVerificationRepository;
import com.group7.minihouse.shared.scheduler.ScheduledJob;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Don ma OTP da het han qua 1 ngay de bang user_verifications khong phinh mai. Chay 3 gio sang moi ngay. */
@Component
public class PurgeExpiredVerificationsJob implements ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(PurgeExpiredVerificationsJob.class);

    private final UserVerificationRepository repo;
    private final Clock clock;

    public PurgeExpiredVerificationsJob(UserVerificationRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override public String name() { return "purge-expired-verifications"; }

    @Override public String description() { return "Xoa ma OTP het han qua 1 ngay"; }

    @Override public String defaultCron() { return "0 0 3 * * *"; }

    @Override
    public void run() {
        int n = repo.deleteExpiredBefore(LocalDateTime.now(clock).minusDays(1));
        log.info("Da xoa {} ma OTP het han", n);
    }
}
