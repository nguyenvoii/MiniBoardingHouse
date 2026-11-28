package com.group7.minihouse.shared.scheduler;

import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Khung chay job tu dong (UC120). Tu tim moi bean ScheduledJob, len lich theo cron khi app khoi dong xong.
 * Gio chay = dong trong bang job_settings (chinh tren giao dien) > app.jobs.<ten>.cron > mac dinh cua job.
 * Cron "-" hoac enabled=false nghia la khong tu chay (van chay tay duoc). Cong tac chay tu dong: app.jobs.enabled
 * hoac minihouse.scheduler.enabled (mac dinh false: khi dev khong tu chay job).
 * Moi job chi chay 1 ban cung luc; loi 1 job duoc ghi lai, khong anh huong job khac. Trang thai lan chay giu trong
 * bo nho (mat khi tat app), du de xem lan chay gan nhat.
 */
@Service
public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);
    public static final String OFF = "-";

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final boolean enabled;
    private final JobSettingRepository settings;
    private volatile boolean started;

    /** Dung trong test: khong co bang cau hinh, chi dung cron mac dinh/properties. */
    public JobScheduler(List<ScheduledJob> jobs, TaskScheduler taskScheduler, Environment env, Clock clock,
                        boolean enabled) {
        this(jobs, taskScheduler, env, clock, enabled, null);
    }

    @Autowired
    public JobScheduler(List<ScheduledJob> jobs, TaskScheduler taskScheduler, Environment env, Clock clock,
                        @Value("${app.jobs.enabled:false}") boolean jobsEnabled,
                        @Value("${minihouse.scheduler.enabled:false}") boolean schedulerEnabled,
                        JobSettingRepository settings) {
        this(jobs, taskScheduler, env, clock, jobsEnabled || schedulerEnabled, settings);
    }

    private JobScheduler(List<ScheduledJob> jobs, TaskScheduler taskScheduler, Environment env, Clock clock,
                         boolean enabled, JobSettingRepository settings) {
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.enabled = enabled;
        this.settings = settings;
        for (ScheduledJob job : jobs) {
            String cron = env.getProperty("app.jobs." + job.name() + ".cron", job.defaultCron());
            if (entries.put(job.name(), new Entry(job, cron)) != null) {
                throw new IllegalStateException("Hai job trùng tên: " + job.name());
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        started = true;
        if (settings != null) {
            for (JobSetting s : settings.findAll()) {
                Entry e = entries.get(s.getJobName());
                if (e != null) {
                    e.override(s.getCron(), Boolean.TRUE.equals(s.getEnabled()));
                }
            }
        }
        if (!enabled) {
            log.info("Job tu dong dang TAT (app.jobs.enabled / minihouse.scheduler.enabled = false), chi chay tay duoc. "
                    + "{} job da dang ky.", entries.size());
            return;
        }
        for (Entry e : entries.values()) {
            schedule(e);
        }
    }

    private void schedule(Entry e) {
        if (e.future != null) {
            e.future.cancel(false);
            e.future = null;
        }
        String cron = e.effectiveCron();
        if (OFF.equals(cron)) {
            return;
        }
        // CronTrigger nem IllegalArgumentException neu cron sai => app bao loi ngay luc khoi dong
        e.future = taskScheduler.schedule(() -> execute(e), new CronTrigger(cron));
        log.info("Da len lich job '{}' theo cron '{}'", e.job.name(), cron);
    }

    public List<JobStatus> list() {
        return entries.values().stream().map(e -> e.status(enabled)).toList();
    }

    /** UC120: doi lich chay cua 1 job. cron 6 truong cua Spring hoac "-" (khong tu chay). Luu DB va ap dung ngay. */
    @Transactional
    public JobStatus updateSchedule(String name, String cron, Boolean jobEnabled, Integer userId) {
        Entry e = entries.get(name);
        if (e == null) {
            throw BusinessException.notFound("Job");
        }
        String value = cron == null ? "" : cron.trim();
        if (value.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cần cron (6 trường) hoặc dấu - để tắt tự chạy");
        }
        if (!OFF.equals(value) && !CronExpression.isValidExpression(value)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Cron không hợp lệ, cần 6 trường: giây phút giờ ngày tháng thứ (ví dụ 0 0 8 * * *)");
        }
        if (value.length() > 60) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cron tối đa 60 ký tự");
        }
        boolean on = jobEnabled == null || jobEnabled;
        if (settings != null) {
            JobSetting row = settings.findById(name).orElseGet(() -> {
                JobSetting n = new JobSetting();
                n.setJobName(name);
                return n;
            });
            row.setCron(value);
            row.setEnabled(on);
            row.setUpdatedBy(userId);
            row.setUpdatedAt(LocalDateTime.now(clock));
            settings.save(row);
        }
        e.override(value, on);
        if (enabled && started) {
            schedule(e);
        }
        return e.status(enabled);
    }

    /** Chay ngay (dong bo) va tra trang thai sau khi chay. 404 neu khong co job, 409 neu job dang chay. */
    public JobStatus runNow(String name) {
        Entry e = entries.get(name);
        if (e == null) {
            throw BusinessException.notFound("Job");
        }
        if (!execute(e)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Job đang chạy, vui lòng đợi chạy xong");
        }
        return e.status(enabled);
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
        final String defaultCron;
        volatile String overrideCron;
        volatile boolean overrideEnabled = true;
        volatile ScheduledFuture<?> future;
        final AtomicBoolean running = new AtomicBoolean();
        volatile LocalDateTime lastStart;
        volatile LocalDateTime lastEnd;
        volatile String lastResult;
        volatile String lastError;

        Entry(ScheduledJob job, String defaultCron) {
            this.job = job;
            this.defaultCron = defaultCron;
        }

        void override(String cron, boolean on) {
            this.overrideCron = cron;
            this.overrideEnabled = on;
        }

        String effectiveCron() {
            if (overrideCron == null) {
                return defaultCron;
            }
            return overrideEnabled ? overrideCron : OFF;
        }

        JobStatus status(boolean schedulerOn) {
            String cron = effectiveCron();
            return new JobStatus(job.name(), job.description(), cron, running.get(), lastStart, lastEnd, lastResult,
                    lastError, schedulerOn && !OFF.equals(cron), overrideCron != null);
        }
    }
}
