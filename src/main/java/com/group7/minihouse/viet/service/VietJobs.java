package com.group7.minihouse.viet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job tu dong cua Viet. Chi chay khi bat minihouse.scheduler.enabled=true (mac dinh TAT).
 * 00:30 cap nhat trang thai hop dong sap het han (UC52); 07:30 tinh lai cong no + tien phat (UC95-96);
 * 08:30 nhac no tu dong (UC99). Doi gio bang minihouse.scheduler.*-cron.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "minihouse.scheduler.enabled", havingValue = "true")
public class VietJobs {

    private static final Logger log = LoggerFactory.getLogger(VietJobs.class);

    private final ContractExpiryService contractExpiryService;
    private final DebtService debtService;

    public VietJobs(ContractExpiryService contractExpiryService, DebtService debtService) {
        this.contractExpiryService = contractExpiryService;
        this.debtService = debtService;
    }

    @Scheduled(cron = "${minihouse.scheduler.contract-expiry-cron:0 30 0 * * *}")
    public void refreshContracts() {
        log.info("Cap nhat hop dong sap het han: {}", contractExpiryService.refreshStatuses());
    }

    @Scheduled(cron = "${minihouse.scheduler.debt-recalc-cron:0 30 7 * * *}")
    public void recalculateDebts() {
        log.info("Tinh lai cong no: {}", debtService.recalculate());
    }

    @Scheduled(cron = "${minihouse.scheduler.debt-reminder-cron:0 30 8 * * *}")
    public void sendDebtReminders() {
        log.info("Nhac no tu dong: {}", debtService.sendReminders());
    }
}
