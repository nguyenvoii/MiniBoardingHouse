package com.group7.minihouse.shared.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.group7.minihouse.shared.exception.BusinessException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

/** Khung chay job tu dong (UC120): dang ky theo cron, chay tay, ghi trang thai, khong cho chay chong. */
class JobSchedulerTest {

    static class FakeJob implements ScheduledJob {
        final String name;
        final AtomicInteger runs = new AtomicInteger();
        RuntimeException failure;

        FakeJob(String name) { this.name = name; }

        @Override public String name() { return name; }
        @Override public String description() { return "Job " + name; }
        @Override public String defaultCron() { return "0 0 3 * * *"; }
        @Override public void run() { runs.incrementAndGet(); if (failure != null) throw failure; }
    }

    final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    final Clock clock = Clock.fixed(java.time.Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC);

    private JobScheduler scheduler(boolean enabled, MockEnvironment env, ScheduledJob... jobs) {
        return new JobScheduler(List.of(jobs), taskScheduler, env, clock, enabled);
    }

    @Test
    void startSchedulesEveryJobWhenEnabled() {
        JobScheduler s = scheduler(true, new MockEnvironment(), new FakeJob("a"), new FakeJob("b"));
        s.start();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void updateScheduleChangesCronAndReschedulesRunningJob() {
        JobScheduler s = scheduler(true, new MockEnvironment(), new FakeJob("a"));
        s.start();
        org.mockito.ArgumentCaptor<Trigger> cap = org.mockito.ArgumentCaptor.forClass(Trigger.class);
        s.updateSchedule("a", "0 15 9 * * *", true, 1);
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), cap.capture());
        assertEquals(new CronTrigger("0 15 9 * * *"), cap.getAllValues().get(1));
        assertEquals("0 15 9 * * *", s.list().get(0).cron());
        assertTrue(s.list().get(0).overridden());
    }

    @Test
    void disablingAJobOrUsingDashStopsAutoRunButManualRunStillWorks() {
        FakeJob job = new FakeJob("a");
        JobScheduler s = scheduler(true, new MockEnvironment(), job);
        s.start();
        s.updateSchedule("a", "0 0 8 * * *", false, 1);
        assertEquals("-", s.list().get(0).cron());
        assertEquals(false, s.list().get(0).scheduled());
        s.runNow("a");
        assertEquals(1, job.runs.get());
        s.updateSchedule("a", "-", true, 1);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class)); // chi lan khoi dong dau
    }

    @Test
    void invalidCronOrUnknownJobIsRejected() {
        JobScheduler s = scheduler(true, new MockEnvironment(), new FakeJob("a"));
        BusinessException bad = assertThrows(BusinessException.class, () -> s.updateSchedule("a", "khong phai cron", true, 1));
        assertEquals(HttpStatus.BAD_REQUEST, bad.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class, () -> s.updateSchedule("a", " ", true, 1)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> s.updateSchedule("zzz", "0 0 8 * * *", true, 1)).getStatus());
        assertEquals("0 0 3 * * *", s.list().get(0).cron()); // cron cu giu nguyen
    }

    @Test
    void startDoesNothingWhenDisabled() {
        JobScheduler s = scheduler(false, new MockEnvironment(), new FakeJob("a"));
        s.start();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void cronCanBeOverriddenByProperty() {
        MockEnvironment env = new MockEnvironment().withProperty("app.jobs.a.cron", "0 30 1 * * *");
        JobScheduler s = scheduler(true, env, new FakeJob("a"));
        s.start();

        org.mockito.ArgumentCaptor<Trigger> cap = org.mockito.ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), cap.capture());
        assertEquals(new CronTrigger("0 30 1 * * *"), cap.getValue());
        assertEquals("0 30 1 * * *", s.list().get(0).cron());
    }

    @Test
    void invalidCronFailsFastAtStartup() {
        MockEnvironment env = new MockEnvironment().withProperty("app.jobs.a.cron", "khong phai cron");
        JobScheduler s = scheduler(true, env, new FakeJob("a"));
        assertThrows(IllegalArgumentException.class, s::start);
    }

    @Test
    void runNowRecordsSuccess() {
        FakeJob job = new FakeJob("a");
        JobScheduler s = scheduler(false, new MockEnvironment(), job);

        JobStatus st = s.runNow("a");

        assertEquals(1, job.runs.get());
        assertEquals("SUCCESS", st.lastResult());
        assertNotNull(st.lastStart());
        assertNull(st.lastError());
        assertEquals("SUCCESS", s.list().get(0).lastResult());
    }

    @Test
    void failureIsRecordedNotThrown() {
        FakeJob job = new FakeJob("a");
        job.failure = new IllegalStateException("hong roi");
        JobScheduler s = scheduler(false, new MockEnvironment(), job);

        JobStatus st = s.runNow("a");

        assertEquals("FAILED", st.lastResult());
        assertTrue(st.lastError().contains("hong roi"));
        // lan sau van chay duoc
        job.failure = null;
        assertEquals("SUCCESS", s.runNow("a").lastResult());
    }

    @Test
    void unknownJobIs404() {
        JobScheduler s = scheduler(false, new MockEnvironment(), new FakeJob("a"));
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(BusinessException.class, () -> s.runNow("khong-co")).getStatus());
    }

    @Test
    void sameJobCannotRunTwiceAtOnce() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ScheduledJob slow = new FakeJob("slow") {
            @Override public void run() {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        };
        JobScheduler s = scheduler(false, new MockEnvironment(), slow);

        Thread t = new Thread(() -> s.runNow("slow"));
        t.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertTrue(s.list().get(0).running());
        assertEquals(HttpStatus.CONFLICT,
                assertThrows(BusinessException.class, () -> s.runNow("slow")).getStatus());
        release.countDown();
        t.join(5000);
        assertEquals(false, s.list().get(0).running());
    }
}
