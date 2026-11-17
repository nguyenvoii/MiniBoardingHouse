package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.entity.CheckoutRequest;
import com.group7.minihouse.shared.entity.DepositRefundVoucher;
import com.group7.minihouse.shared.entity.RentalContract;
import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableData;
import com.group7.minihouse.viet.dto.CheckoutReportDtos.CheckoutReportResponse;
import com.group7.minihouse.viet.repository.DepositRefundVoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** UC132: báo cáo hoàn cọc theo tháng, không cần DB. */
class CheckoutReportServiceTest {

    private final DepositRefundVoucherRepository repo = mock(DepositRefundVoucherRepository.class);
    private final CheckoutReportService service = new CheckoutReportService(repo);

    private static DepositRefundVoucher voucher(int id, String room, String status, LocalDate actual,
                                                LocalDate expected, String deposit, String debt, String damage,
                                                String refund) {
        Room r = new Room();
        r.setRoomCode(room);
        Tenant t = new Tenant();
        t.setFullName("Khách " + room);
        RentalContract k = new RentalContract();
        k.setRoom(r);
        k.setTenant(t);
        CheckoutRequest c = new CheckoutRequest();
        c.setCheckoutId(id);
        c.setContract(k);
        c.setActualCheckoutDate(actual);
        c.setExpectedCheckoutDate(expected);
        DepositRefundVoucher v = new DepositRefundVoucher();
        v.setCheckout(c);
        v.setStatus(status);
        v.setOriginalDepositAmount(new BigDecimal(deposit));
        v.setDeductedDebtAmount(new BigDecimal(debt));
        v.setDeductedDamageAmount(new BigDecimal(damage));
        v.setActualRefundAmount(new BigDecimal(refund));
        if ("REFUNDED".equals(status)) {
            v.setRefundDate(LocalDateTime.of(2026, 11, 10, 9, 0));
        }
        return v;
    }

    private void stubTwoVouchers() {
        when(repo.findForMonthReport(any(), any())).thenReturn(List.of(
                voucher(2, "P102", "PENDING", null, LocalDate.of(2026, 11, 25),
                        "3000000", "500000", "0", "2500000"),
                voucher(1, "P101", "REFUNDED", LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9),
                        "3000000", "1000000", "200000", "1800000")));
    }

    @Test
    void thangSaiDinhDangPhaiTuChoi() {
        for (String bad : new String[] {"2026-13", "abc", "11/2026"}) {
            BusinessException ex = assertThrows(BusinessException.class, () -> service.report(bad));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    @Test
    void thieuThangPhaiTuChoi() {
        assertThrows(BusinessException.class, () -> service.report(null));
        assertThrows(BusinessException.class, () -> service.report("  "));
    }

    @Test
    void truyVanDungNgayDauVaCuoiThang() {
        when(repo.findForMonthReport(any(), any())).thenReturn(List.of());
        service.report("2026-02");
        verify(repo).findForMonthReport(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    @Test
    void thangKhongCoPhieuTraVeBaoCaoRongTongBangKhong() {
        when(repo.findForMonthReport(any(), any())).thenReturn(List.of());
        CheckoutReportResponse r = service.report("2026-11");
        assertTrue(r.rows().isEmpty());
        assertEquals(0, r.totals().totalDeposit().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.totals().pendingCount());
    }

    @Test
    void tongTinhDungVaChiTinhPhieuDaHoanVaoTongDaHoan() {
        stubTwoVouchers();
        CheckoutReportResponse r = service.report("2026-11");
        assertEquals(2, r.rows().size());
        assertEquals(0, new BigDecimal("6000000").compareTo(r.totals().totalDeposit()));
        assertEquals(0, new BigDecimal("1500000").compareTo(r.totals().totalDeductedDebt()));
        assertEquals(0, new BigDecimal("200000").compareTo(r.totals().totalDeductedDamage()));
        assertEquals(0, new BigDecimal("1800000").compareTo(r.totals().totalRefunded()));
        assertEquals(1, r.totals().pendingCount());
        assertEquals(0, new BigDecimal("2500000").compareTo(r.totals().pendingAmount()));
    }

    @Test
    void phieuChoHoanLayNgayDuKienVaDongSapTheoNgay() {
        stubTwoVouchers();
        CheckoutReportResponse r = service.report("2026-11");
        assertEquals("P101", r.rows().get(0).roomCode());
        assertEquals(LocalDate.of(2026, 11, 25), r.rows().get(1).checkoutDate());
    }

    @Test
    void cotTienNullDuocXemLaKhong() {
        DepositRefundVoucher v = voucher(3, "P103", "REFUNDED", LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 5), "2000000", "0", "0", "2000000");
        v.setDeductedDebtAmount(null);
        v.setDeductedDamageAmount(null);
        when(repo.findForMonthReport(any(), any())).thenReturn(List.of(v));
        CheckoutReportResponse r = service.report("2026-11");
        assertEquals(0, BigDecimal.ZERO.compareTo(r.rows().get(0).deductedDebt()));
    }

    @Test
    void xuatFileCoDongTongCuoiVaDungSoCot() {
        stubTwoVouchers();
        TableData data = service.exportTable("2026-11");
        assertEquals(3, data.rows().size());
        List<Object> last = data.rows().get(2);
        assertTrue(last.get(0).toString().startsWith("TỔNG"));
        assertTrue(last.get(7).toString().contains("Chưa hoàn: 1"));
        assertEquals("Đã hoàn", data.rows().get(0).get(7));
        assertEquals("Chờ hoàn", data.rows().get(1).get(7));
    }
}
