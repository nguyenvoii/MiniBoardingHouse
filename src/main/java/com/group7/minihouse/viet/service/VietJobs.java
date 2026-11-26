package com.group7.minihouse.viet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Job tu dong cua Viet. Chi chay khi bat minihouse.scheduler.enabled=true (mac dinh TAT).
 * 00:30 cap nhat trang thai hop dong sap het han (UC52); 07:30 tinh lai cong no + tien phat (UC95-96);
 * 08:30 nhac no tu dong (UC99). Doi gio bang minihouse.scheduler.*-cron.
 */
@EnableScheduling
public class VietJobs {

    private static final Logger log = LoggerFactory.getLogger(VietJobs.class);

    private final ContractExpiryService contractExpiryService;
    private final DebtService debtService;

    public VietJobs(ContractExpiryService contractExpiryService, DebtService debtService) {
        this.contractExpiryService = contractExpiryService;
        this.debtService = debtService;
    }

    public void refreshContracts() {
        log.info("Cap nhat hop dong sap het han: {}", contractExpiryService.refreshStatuses());
    }

    public void recalculateDebts() {
        log.info("Tinh lai cong no: {}", debtService.recalculate());
    }

    public void sendDebtReminders() {
        log.info("Nhac no tu dong: {}", debtService.sendReminders());
    }
}
