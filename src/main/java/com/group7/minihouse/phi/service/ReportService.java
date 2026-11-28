package com.group7.minihouse.phi.service;

import com.group7.minihouse.phi.dto.ReportDtos.Dashboard;
import com.group7.minihouse.phi.dto.ReportDtos.MonthRow;
import com.group7.minihouse.phi.dto.ReportDtos.RevenueReport;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC116 dashboard tong quan va UC118 bao cao doanh thu - chi phi theo thang.
 * Doanh thu = tien THUC THU trong ky = giao dich thanh toan SUCCESS (khong tinh REVERSED/dang cho) theo ngay thanh toan
 * + cac dot thanh toan mot phan (partial_payment_logs, hoa don chua huy) theo ngay tra. KHONG gom: tien can tru tu
 * so du khach (do la tien da thu o lan truoc, cong lai se tinh doi) va tien coc gach no luc tra phong (coc da thu luc
 * ky hop dong, chi la doi muc dich su dung); hai khoan nay chi lam hoa don PAID chu khong phai dong tien moi vao;
 * chi phi = operating_expenses theo ngay chi. Cong no = phan chua tra cua hoa don chua tat toan (khong tinh da huy).
 * propertyId null = gop tat ca khu tro.
 */
@Service
public class ReportService {

    private static final String PAYABLE = "('UNPAID', 'PARTIALLY_PAID', 'OVERDUE')";

    private final EntityManager em;
    private final Clock clock;

    public ReportService(EntityManager em, Clock clock) {
        this.em = em;
        this.clock = clock;
    }

    @Cacheable(cacheNames = "dashboard", key = "#propertyId ?: 0")
    @Transactional(readOnly = true)
    public Dashboard dashboard(Integer propertyId) {
        int p = propertyId == null ? 0 : propertyId;
        LocalDate today = LocalDate.now(clock);
        YearMonth ym = YearMonth.from(today);

        long rented = 0;
        long vacant = 0;
        long other = 0;
        for (Object[] row : em.createQuery("select r.status, count(r) from Room r "
                + "where (:p = 0 or r.property.propertyId = :p) group by r.status", Object[].class)
                .setParameter("p", p).getResultList()) {
            long n = ((Number) row[1]).longValue();
            switch ((String) row[0]) {
                case "RENTED" -> rented += n;
                case "VACANT" -> vacant += n;
                default -> other += n;
            }
        }
        long total = rented + vacant + other;

        BigDecimal revenue = money("select sum(t.amountPaid) from PaymentTransaction t where t.status = 'SUCCESS' "
                + "and t.paymentDate >= :from and t.paymentDate < :to "
                + "and (:p = 0 or t.invoice.calculatedFee.billingPeriod.property.propertyId = :p)", p,
                ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay())
                .add(money("select sum(l.amount) from PartialPaymentLog l where l.invoice.paymentStatus <> 'CANCELLED' "
                + "and l.paidDate >= :from and l.paidDate < :to "
                + "and (:p = 0 or l.invoice.calculatedFee.billingPeriod.property.propertyId = :p)", p,
                ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay()));
        BigDecimal billed = money("select sum(i.totalAmount) from Invoice i where i.paymentStatus <> 'CANCELLED' "
                + "and i.issueDate between :from and :to "
                + "and (:p = 0 or i.calculatedFee.billingPeriod.property.propertyId = :p)", p,
                ym.atDay(1), ym.atEndOfMonth());
        BigDecimal expense = money("select sum(e.amount) from OperatingExpense e "
                + "where e.expenseDate between :from and :to and (:p = 0 or e.property.propertyId = :p)", p,
                ym.atDay(1), ym.atEndOfMonth());
        // M8: chi phi sua chua ghi o Su co (maintenance_logs.repair_cost) cung la chi phi cua thang hoan tat
        expense = expense.add(money("select sum(l.repairCost) from MaintenanceLog l where l.repairCost > 0 "
                + "and l.completedAt >= :from and l.completedAt < :to "
                + "and (:p = 0 or l.request.room.property.propertyId = :p)", p,
                ym.atDay(1).atStartOfDay(), ym.plusMonths(1).atDay(1).atStartOfDay()));

        Object[] debt = em.createQuery("select sum(i.totalAmount - i.paidAmount), count(i) from Invoice i "
                + "where i.paymentStatus in " + PAYABLE
                + " and (:p = 0 or i.calculatedFee.billingPeriod.property.propertyId = :p)", Object[].class)
                .setParameter("p", p).getSingleResult();
        BigDecimal outstanding = debt[0] == null ? BigDecimal.ZERO : (BigDecimal) debt[0];
        long unpaid = ((Number) debt[1]).longValue();
        long overdue = em.createQuery("select count(i) from Invoice i where i.paymentStatus in " + PAYABLE
                + " and i.dueDate < :today and (:p = 0 or i.calculatedFee.billingPeriod.property.propertyId = :p)",
                Long.class).setParameter("today", today).setParameter("p", p).getSingleResult();

        long expiring = em.createQuery("select count(c) from RentalContract c where c.status = 'EXPIRING_SOON' "
                + "and (:p = 0 or c.room.property.propertyId = :p)", Long.class)
                .setParameter("p", p).getSingleResult();
        long ending = em.createQuery("select count(c) from RentalContract c "
                + "where c.status in ('ACTIVE', 'EXPIRING_SOON') and c.endDate between :from and :to "
                + "and (:p = 0 or c.room.property.propertyId = :p)", Long.class)
                .setParameter("from", today).setParameter("to", today.plusDays(30))
                .setParameter("p", p).getSingleResult();

        return new Dashboard(propertyId, today, total, rented, vacant, other, occupancy(rented, total), revenue,
                billed, expense, outstanding, unpaid, overdue, expiring, ending);
    }

    @Cacheable(cacheNames = "revenue", key = "(#propertyId ?: 0) + '-' + (#year ?: 0)")
    @Transactional(readOnly = true)
    public RevenueReport revenueReport(Integer propertyId, Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        if (y < 2000 || y > 2100) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Năm không hợp lệ");
        }
        int p = propertyId == null ? 0 : propertyId;
        Map<Integer, BigDecimal> revenue = byMonth("select month(t.paymentDate), sum(t.amountPaid) "
                + "from PaymentTransaction t where t.status = 'SUCCESS' and year(t.paymentDate) = :y "
                + "and (:p = 0 or t.invoice.calculatedFee.billingPeriod.property.propertyId = :p) "
                + "group by month(t.paymentDate)", y, p);
        byMonth("select month(l.paidDate), sum(l.amount) from PartialPaymentLog l "
                + "where l.invoice.paymentStatus <> 'CANCELLED' and year(l.paidDate) = :y "
                + "and (:p = 0 or l.invoice.calculatedFee.billingPeriod.property.propertyId = :p) "
                + "group by month(l.paidDate)", y, p).forEach((m, v) -> revenue.merge(m, v, BigDecimal::add));
        Map<Integer, BigDecimal> expense = byMonth("select month(e.expenseDate), sum(e.amount) "
                + "from OperatingExpense e where year(e.expenseDate) = :y "
                + "and (:p = 0 or e.property.propertyId = :p) group by month(e.expenseDate)", y, p);
        byMonth("select month(l.completedAt), sum(l.repairCost) from MaintenanceLog l where l.repairCost > 0 "
                + "and year(l.completedAt) = :y and (:p = 0 or l.request.room.property.propertyId = :p) "
                + "group by month(l.completedAt)", y, p).forEach((m, v) -> expense.merge(m, v, BigDecimal::add));
        return buildReport(propertyId, y, revenue, expense);
    }

    /** Bang de xuat Excel/PDF (UC118): 12 thang + dong tong. */
    public static TableData toTable(RevenueReport r) {
        List<List<Object>> rows = new ArrayList<>();
        for (MonthRow m : r.months()) {
            rows.add(List.of("Tháng " + m.month(), m.revenue(), m.expense(), m.profit()));
        }
        rows.add(List.of("Cả năm", r.totalRevenue(), r.totalExpense(), r.totalProfit()));
        return new TableData("Báo cáo doanh thu - chi phí năm " + r.year(),
                List.of("Tháng", "Doanh thu", "Chi phí", "Lợi nhuận"), rows);
    }

    // ------------------------------------------------------------------ cong thuc thuan (de test)

    static RevenueReport buildReport(Integer propertyId, int year, Map<Integer, BigDecimal> revenue,
                                     Map<Integer, BigDecimal> expense) {
        List<MonthRow> months = new ArrayList<>();
        BigDecimal tr = BigDecimal.ZERO;
        BigDecimal te = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            BigDecimal r = revenue.getOrDefault(m, BigDecimal.ZERO);
            BigDecimal e = expense.getOrDefault(m, BigDecimal.ZERO);
            months.add(new MonthRow(m, r, e, r.subtract(e)));
            tr = tr.add(r);
            te = te.add(e);
        }
        return new RevenueReport(propertyId, year, months, tr, te, tr.subtract(te));
    }

    static BigDecimal occupancy(long rented, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(rented * 100L).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ noi bo

    private BigDecimal money(String jpql, int p, Object from, Object to) {
        BigDecimal v = em.createQuery(jpql, BigDecimal.class).setParameter("p", p)
                .setParameter("from", from).setParameter("to", to).getSingleResult();
        return v == null ? BigDecimal.ZERO : v;
    }

    private Map<Integer, BigDecimal> byMonth(String jpql, int year, int p) {
        Map<Integer, BigDecimal> out = new HashMap<>();
        for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("y", year).setParameter("p", p)
                .getResultList()) {
            out.put(((Number) row[0]).intValue(), (BigDecimal) row[1]);
        }
        return out;
    }
}
