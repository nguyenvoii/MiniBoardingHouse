package com.group7.minihouse.shared.scheduler;

import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

/**
 * Khung chay job tu dong (UC120). Tu tim moi bean ScheduledJob, len lich theo cron khi app khoi dong xong.
 * Cong tac app.jobs.enabled (mac dinh false: khi dev khong tu chay job, chi chay tay qua API).
 * Moi job chi chay 1 ban cung luc; loi 1 job duoc ghi lai, khong anh huong job khac. Trang thai giu trong bo nho
 * (mat khi tat app), du de xem lan chay gan nhat.
 */
@Service
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final boolean enabled;

    public JobScheduler(List<ScheduledJob> jobs, TaskScheduler taskScheduler, Environment env, Clock clock,
                        @Value("${app.jobs.enabled:false}") boolean enabled) {
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.enabled = enabled;
        for (ScheduledJob job : jobs) {
            String cron = env.getProperty("app.jobs." + job.name() + ".cron", job.defaultCron());
            if (entries.put(job.name(), new Entry(job, cron)) != null) {
                throw new IllegalStateException("Hai job trung ten: " + job.name());
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            log.info("Job tu dong dang TAT (app.jobs.enabled=false), chi chay tay duoc. {} job da dang ky.",
                    entries.size());
            return;
        }
        for (Entry e : entries.values()) {
            // CronTrigger nem IllegalArgumentException neu cron sai => app bao loi ngay luc khoi dong
            taskScheduler.schedule(() -> execute(e), new CronTrigger(e.cron));
            log.info("Da len lich job '{}' theo cron '{}'", e.job.name(), e.cron);
        }
    }

    public List<JobStatus> list() {
        return entries.values().stream().map(Entry::status).toList();
    }

    /** Chay ngay (dong bo) va tra trang thai sau khi chay. 404 neu khong co job, 409 neu job dang chay. */
    public JobStatus runNow(String name) {
        Entry e = entries.get(name);
        if (e == null) {
            throw BusinessException.notFound("Job");
        }
        if (!execute(e)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Job dang chay, vui long doi chay xong");
        }
        return e.status();
    }

    /** false neu job dang chay (bo qua lan nay). */
    private boolean execute(Entry e) {
        if (!e.running.compareAndSet(false, true)) {
            log.warn("Job '{}' van dang chay, bo qua lan goi nay", e.job.name());
            return false;
        }
        e.lastStart = LocalDateTime.now(clock);
        e.lastEnd = null;
        try {
            e.job.run();
            e.lastResult = "SUCCESS";
            e.lastError = null;
        } catch (Exception ex) {
            log.error("Job '{}' loi", e.job.name(), ex);
            e.lastResult = "FAILED";
            e.lastError = String.valueOf(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        } finally {
            e.lastEnd = LocalDateTime.now(clock);
            e.running.set(false);
        }
        return true;
    }

    private static final class Entry {
        final ScheduledJob job;
        final String cron;
        final AtomicBoolean running = new AtomicBoolean();
        volatile LocalDateTime lastStart;
        volatile LocalDateTime lastEnd;
        volatile String lastResult;
        volatile String lastError;

        Entry(ScheduledJob job, String cron) {
            this.job = job;
            this.cron = cron;
        }

        JobStatus status() {
            return new JobStatus(job.name(), job.description(), cron, running.get(), lastStart, lastEnd,
                    lastResult, lastError);
        }
    }
}
