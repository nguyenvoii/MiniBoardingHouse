package com.group7.minihouse.shared.scheduler;

import com.group7.minihouse.phi.repository.BillingPeriodRepository;
import com.group7.minihouse.phi.service.BillingService;
import com.group7.minihouse.toan.service.BillingJobs;
import com.group7.minihouse.toan.service.InvoiceService;
import com.group7.minihouse.toan.service.MeterReadingService;
import com.group7.minihouse.viet.service.ContractExpiryService;
import com.group7.minihouse.viet.service.DebtService;
import com.group7.minihouse.viet.service.VietJobs;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * UC120: dang ky cac tac vu tu dong that (chot chi so, tinh tien, phat hanh, nhac han, hop dong het han, cong no) vao
 * khung JobScheduler: xem lich, ket qua lan chay gan nhat, chay tay va doi gio chay tren giao dien. Cron mac dinh lay
 * tu minihouse.scheduler.*-cron (tuong thich cau hinh cu), bi ghi de boi bang job_settings.
 */
@Configuration
public class AutomationJobsConfig {

    private static final String PREFIX = "minihouse.scheduler.";

    private static ScheduledJob job(Environment env, String name, String description, String cronKey, String fallback,
                                    ThrowingRunnable body) {
        return new ScheduledJob() {
            public String name() { return name; }
            public String description() { return description; }
            public String defaultCron() { return env.getProperty(PREFIX + cronKey, fallback); }
            public void run() throws Exception { body.run(); }
        };
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Bean
    ScheduledJob meterCloseJob(Environment env, InvoiceService invoices, MeterReadingService meters,
                               BillingPeriodRepository periods, BillingService billing, Clock clock,
                               @Value("${minihouse.scheduler.meter-grace-days:7}") int graceDays) {
        BillingJobs jobs = new BillingJobs(invoices, meters, periods, billing, clock, graceDays);
        return job(env, "meter-close", "Chốt kỳ ghi chỉ số quá hạn", "meter-close-cron", "0 0 1 * * *",
                jobs::closeExpiredMeterPeriods);
    }

    @Bean
    ScheduledJob billingPrepareJob(Environment env, InvoiceService invoices, MeterReadingService meters,
                                   BillingPeriodRepository periods, BillingService billing, Clock clock,
                                   @Value("${minihouse.scheduler.meter-grace-days:7}") int graceDays) {
        BillingJobs jobs = new BillingJobs(invoices, meters, periods, billing, clock, graceDays);
        return job(env, "billing-prepare", "Tính nháp tiền tháng trước (chờ nhân viên duyệt)", "billing-calculate-cron",
                "-", jobs::prepareBillingDrafts);
    }

    @Bean
    ScheduledJob invoicePublishJob(Environment env, InvoiceService invoices, MeterReadingService meters,
                                   BillingPeriodRepository periods, BillingService billing, Clock clock,
                                   @Value("${minihouse.scheduler.meter-grace-days:7}") int graceDays) {
        BillingJobs jobs = new BillingJobs(invoices, meters, periods, billing, clock, graceDays);
        return job(env, "invoice-publish", "Phát hành hóa đơn của kỳ đã duyệt", "invoice-publish-cron", "-",
                jobs::publishApprovedInvoices);
    }

    @Bean
    ScheduledJob invoiceReminderJob(Environment env, InvoiceService invoices, MeterReadingService meters,
                                    BillingPeriodRepository periods, BillingService billing, Clock clock,
                                    @Value("${minihouse.scheduler.meter-grace-days:7}") int graceDays) {
        BillingJobs jobs = new BillingJobs(invoices, meters, periods, billing, clock, graceDays);
        return job(env, "invoice-reminders", "Nhắc hạn thanh toán và đánh dấu hóa đơn quá hạn", "invoice-cron",
                "0 0 8 * * *", jobs::remindAndMarkOverdue);
    }

    @Bean
    ScheduledJob contractExpiryJob(Environment env, ContractExpiryService expiry, DebtService debts) {
        VietJobs jobs = new VietJobs(expiry, debts);
        return job(env, "contract-expiry", "Cập nhật hợp đồng sắp hết hạn", "contract-expiry-cron", "0 30 0 * * *",
                jobs::refreshContracts);
    }

    @Bean
    ScheduledJob debtRecalcJob(Environment env, ContractExpiryService expiry, DebtService debts) {
        VietJobs jobs = new VietJobs(expiry, debts);
        return job(env, "debt-recalc", "Tính lại công nợ", "debt-recalc-cron", "0 30 7 * * *", jobs::recalculateDebts);
    }

    @Bean
    ScheduledJob debtReminderJob(Environment env, ContractExpiryService expiry, DebtService debts) {
        VietJobs jobs = new VietJobs(expiry, debts);
        return job(env, "debt-reminders", "Nhắc nợ tự động (lần 1, lần 2, quá hạn)", "debt-reminder-cron",
                "0 30 8 * * *", jobs::sendDebtReminders);
    }
}
