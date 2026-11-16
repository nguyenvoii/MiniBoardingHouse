package com.group7.minihouse.phi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.group7.minihouse.phi.repository.UserVerificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PurgeExpiredVerificationsJobTest {

    @Test
    void deletesCodesExpiredMoreThanOneDayAgo() {
        UserVerificationRepository repo = mock(UserVerificationRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);
        PurgeExpiredVerificationsJob job = new PurgeExpiredVerificationsJob(repo, clock);

        job.run();

        verify(repo).deleteExpiredBefore(LocalDateTime.of(2026, 11, 15, 3, 0));
        assertEquals("purge-expired-verifications", job.name());
    }
}
