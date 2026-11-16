package com.group7.minihouse.shared.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    /** 2 luong cho job (job khac nhau co the chay cung luc, cung 1 job thi khong). */
    @Bean
    ThreadPoolTaskScheduler jobTaskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("job-");
        return s;
    }
}
