package com.group7.minihouse.toan.service;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cac job tu dong cua Toan (UC 57, 82). Chi chay khi bat minihouse.scheduler.enabled=true (mac dinh TAT, cung
 * quy uoc voi VietJobs):
 * - chot ky ghi chi so dien nuoc qua han,
 * - nhac thanh toan truoc han 3 ngay & 1 ngay,
 * - chuyen hoa don qua han sang OVERDUE va thong bao.
 *
 * Cau hinh trong application.properties:
 *   minihouse.scheduler.enabled=false          (tat toan bo job)
 *   minihouse.scheduler.invoice-cron=...       (mac dinh 8h sang moi ngay)
 *   minihouse.scheduler.meter-close-cron=...   (mac dinh 1h sang moi ngay)
 *   minihouse.scheduler.meter-grace-days=7
 */
@Component
@ConditionalOnProperty(name = "minihouse.scheduler.enabled", havingValue = "true")
public class BillingJobs {

    private static final Logger log = LoggerFactory.getLogger(BillingJobs.class);

    private final InvoiceService invoiceService;
    private final MeterReadingService meterService;
    private final int meterGraceDays;

    public BillingJobs(InvoiceService invoiceService, MeterReadingService meterService,
                       @Value("${minihouse.scheduler.meter-grace-days:7}") int meterGraceDays) {
        this.invoiceService = invoiceService;
        this.meterService = meterService;
        this.meterGraceDays = meterGraceDays;
    }

    @Scheduled(cron = "${minihouse.scheduler.meter-close-cron:0 0 1 * * *}")
    public void closeExpiredMeterPeriods() {
        int n = meterService.autoCloseExpiredPeriods(meterGraceDays, LocalDate.now());
        log.info("Da chot {} ky ghi chi so qua han", n);
    }

    @Scheduled(cron = "${minihouse.scheduler.invoice-cron:0 0 8 * * *}")
    public void remindAndMarkOverdue() {
        LocalDate today = LocalDate.now();
        int before3 = invoiceService.sendDueReminders(3, today);
        int before1 = invoiceService.sendDueReminders(1, today);
        int overdue = invoiceService.markOverdueAndNotify(today);
        log.info("Nhac han: {} (con 3 ngay), {} (con 1 ngay); thong bao qua han: {}", before3, before1, overdue);
    }
}
