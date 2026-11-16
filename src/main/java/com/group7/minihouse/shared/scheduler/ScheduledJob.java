package com.group7.minihouse.shared.scheduler;

/**
 * 1 cong viec tu dong chay theo gio. Cach them job: viet 1 class @Component implements ScheduledJob,
 * khung JobScheduler tu tim thay va len lich, khong phai dang ky o dau khac.
 * Vi du (Toan): chot chi so - tinh hoa don dau thang; (Viet): nhac no, phat tre han.
 * Doi gio chay ma khong sua code: dat app.jobs.&lt;name&gt;.cron trong application-local.properties.
 */
public interface ScheduledJob {

    /** Ten ngan, duy nhat, khong dau cach (vd "monthly-billing"); dung lam khoa cau hinh va ten API chay tay. */
    String name();

    String description();

    /** Cron 6 truong cua Spring: giay phut gio ngay thang thu. Vi du "0 0 3 * * *" = 3 gio sang moi ngay. */
    String defaultCron();

    /** Noi dung cong viec. Nem loi thi khung ghi nhan FAILED, khong lam hong cac job khac. */
    void run() throws Exception;
}
