package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.group7.minihouse.shared.entity.Invoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Quy tac duy nhat cap nhat so da tra + trang thai hoa don (khong can DB). */
class InvoiceBalanceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);
    private final InvoiceBalanceCalculator calc = new InvoiceBalanceCalculator();

    private static Invoice invoice(String total, String paid, LocalDate due, String status) {
        Invoice i = new Invoice();
        i.setTotalAmount(new BigDecimal(total));
        i.setPaidAmount(paid == null ? null : new BigDecimal(paid));
        i.setDueDate(due);
        i.setPaymentStatus(status);
        return i;
    }

    @Test
    void chuaTraDongThoiHanLaUnpaid() {
        Invoice i = invoice("1000", "0", TODAY.plusDays(3), "PAID");
        calc.refreshStatus(i, TODAY);
        assertEquals("UNPAID", i.getPaymentStatus());
    }

    @Test
    void traMotPhanTrongHanLaPartiallyPaid() {
        Invoice i = invoice("1000", "400", TODAY.plusDays(3), "UNPAID");
        calc.refreshStatus(i, TODAY);
        assertEquals("PARTIALLY_PAID", i.getPaymentStatus());
    }

    @Test
    void quaHanChuaTraDuLaOverdueKeCaDaTraMotPhan() {
        Invoice a = invoice("1000", "0", TODAY.minusDays(1), "UNPAID");
        Invoice b = invoice("1000", "400", TODAY.minusDays(1), "PARTIALLY_PAID");
        calc.refreshStatus(a, TODAY);
        calc.refreshStatus(b, TODAY);
        assertEquals("OVERDUE", a.getPaymentStatus());
        assertEquals("OVERDUE", b.getPaymentStatus());
    }

    @Test
    void ngayDenHanChinhNgayHomNayChuaLaQuaHan() {
        Invoice i = invoice("1000", "0", TODAY, "UNPAID");
        calc.refreshStatus(i, TODAY);
        assertEquals("UNPAID", i.getPaymentStatus());
    }

    @Test
    void traDuHoacVuotLaPaidKeCaQuaHan() {
        Invoice exact = invoice("1000", "1000", TODAY.minusDays(9), "OVERDUE");
        Invoice over = invoice("1000", "1200", TODAY.plusDays(9), "UNPAID");
        calc.refreshStatus(exact, TODAY);
        calc.refreshStatus(over, TODAY);
        assertEquals("PAID", exact.getPaymentStatus());
        assertEquals("PAID", over.getPaymentStatus());
    }

    @Test
    void hoaDonTongKhongLaPaid() {
        Invoice i = invoice("0", "0", TODAY.plusDays(5), "UNPAID");
        calc.refreshStatus(i, TODAY);
        assertEquals("PAID", i.getPaymentStatus());
    }

    @Test
    void hoaDonDaHuyKhongDoiTrangThaiVaKhongConPhaiTra() {
        Invoice i = invoice("1000", "0", TODAY.minusDays(5), "CANCELLED");
        calc.setPaid(i, new BigDecimal("1000"), TODAY);
        assertEquals("CANCELLED", i.getPaymentStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(calc.remainingOf(i)));
    }

    @Test
    void paidNullTinhLaKhongVaConLaBangTong() {
        Invoice i = invoice("1000", null, TODAY.plusDays(1), "UNPAID");
        assertEquals(0, BigDecimal.ZERO.compareTo(calc.paidOf(i)));
        assertEquals(0, new BigDecimal("1000").compareTo(calc.remainingOf(i)));
    }

    @Test
    void setPaidTinhLaiTrangThaiTheoSoMoi() {
        Invoice i = invoice("1000", "0", TODAY.plusDays(2), "UNPAID");
        calc.setPaid(i, new BigDecimal("300"), TODAY);
        assertEquals("PARTIALLY_PAID", i.getPaymentStatus());
        calc.setPaid(i, new BigDecimal("1000"), TODAY);
        assertEquals("PAID", i.getPaymentStatus());
        calc.setPaid(i, new BigDecimal("0"), TODAY);
        assertEquals("UNPAID", i.getPaymentStatus());
    }
}
