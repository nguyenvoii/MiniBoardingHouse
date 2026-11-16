package com.group7.minihouse.shared.scheduler;

import java.time.LocalDateTime;

/** Trang thai 1 job: lastResult la SUCCESS, FAILED hoac null (chua chay lan nao tu luc bat app). */
public record JobStatus(String name, String description, String cron, boolean running, LocalDateTime lastStart,
                        LocalDateTime lastEnd, String lastResult, String lastError) {
}
