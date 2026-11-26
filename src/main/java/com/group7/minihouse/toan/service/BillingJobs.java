package com.group7.minihouse.toan.service;

import com.group7.minihouse.phi.repository.BillingPeriodRepository;
import com.group7.minihouse.phi.service.BillingService;
import com.group7.minihouse.toan.dto.InvoiceDtos.IssueBatchRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

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
public class BillingJobs {

    private static final Logger log = LoggerFactory.getLogger(BillingJobs.class);

    private final InvoiceService invoiceService;
    private final MeterReadingService meterService;
    private final int meterGraceDays;
    private final BillingPeriodRepository billingPeriods;
    private final Clock clock;
    private final BillingService billingService;

    public BillingJobs(InvoiceService invoiceService, MeterReadingService meterService,
                       BillingPeriodRepository billingPeriods, BillingService billingService, Clock clock,
                       @Value("${minihouse.scheduler.meter-grace-days:7}") int meterGraceDays) {
        this.invoiceService = invoiceService;
        this.meterService = meterService;
        this.meterGraceDays = meterGraceDays;
        this.billingPeriods = billingPeriods;
        this.billingService = billingService;
        this.clock = clock;
    }

    public void closeExpiredMeterPeriods() {
        int n = meterService.autoCloseExpiredPeriods(meterGraceDays, LocalDate.now());
        log.info("Da chot {} ky ghi chi so qua han", n);
    }

    /** Opt-in. Prepare empty drafts only, leaving existing calculations for staff review. */
    public void prepareBillingDrafts() {
        String previousMonth = YearMonth.now(clock).minusMonths(1).toString();
        for (var period : billingPeriods.findByStatusAndMonthYearOrderByBillingPeriodId("DRAFT", previousMonth)) {
            Integer id = period.getBillingPeriodId();
            try {
                if (billingService.calculateUnpreparedPeriod(id)) {
                    log.info("Da tinh tien cho ky {}", id);
                }
            } catch (RuntimeException ex) {
                log.error("Khong the tinh tien cho ky {}", id, ex);
            }
        }
    }

    /** Opt-in. Only periods already approved by staff are eligible for automatic publication. */
    public void publishApprovedInvoices() {
        LocalDate today = LocalDate.now(clock);
        String previousMonth = YearMonth.from(today).minusMonths(1).toString();
        for (var period : billingPeriods.findByStatusAndMonthYearOrderByBillingPeriodId("APPROVED", previousMonth)) {
            Integer id = period.getBillingPeriodId();
            try {
                int count = invoiceService.issueBatch(new IssueBatchRequest(id, today, null)).size();
                log.info("Da phat hanh {} hoa don cho ky {}", count, id);
            } catch (RuntimeException ex) {
                log.error("Khong the phat hanh hoa don cho ky {}", id, ex);
            }
        }
    }

    public void remindAndMarkOverdue() {
        LocalDate today = LocalDate.now();
        int before3 = invoiceService.sendDueReminders(3, today);
        int before1 = invoiceService.sendDueReminders(1, today);
        int overdue = invoiceService.markOverdueAndNotify(today);
        log.info("Nhac han: {} (con 3 ngay), {} (con 1 ngay); thong bao qua han: {}", before3, before1, overdue);
    }
}
