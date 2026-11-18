package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.toan.repository.PaymentReportRepository;
import com.group7.minihouse.toan.repository.ReceiptVoucherRepository;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReportServiceTest {
    @Mock PaymentReportRepository transactions;
    @Mock ReceiptVoucherRepository vouchers;
    @Mock TableExporter exporter;

    @Test
    void tongHopVaDoiSoatGiaoDichThanhCong() {
        LocalDate day = LocalDate.of(2026, 11, 16);
        when(transactions.summarizeSuccessful(any(), any())).thenReturn(java.util.Collections.singletonList(new Object[]{
                Date.valueOf(day), 5, "Thu ngân A", "CASH", 2L, new BigDecimal("500000")}));
        when(transactions.summarizeStatuses(any(), any())).thenReturn(java.util.Arrays.asList(
                new Object[]{"SUCCESS", 2L, new BigDecimal("500000")},
                new Object[]{"REVERSED", 1L, new BigDecimal("100000")},
                new Object[]{"FAILED", 1L, new BigDecimal("50000")}));
        when(vouchers.sumSuccessfulReceipts(any(), any())).thenReturn(new BigDecimal("400000"));

        PaymentReportService service = new PaymentReportService(transactions, vouchers, exporter);
        var report = service.report(day, day);

        assertEquals(1, report.rows().size());
        assertEquals(2L, report.summary().successfulTransactions());
        assertEquals(new BigDecimal("400000"), report.summary().netCollectedAmount());
        assertEquals(new BigDecimal("0"), report.summary().difference());
    }

    @Test
    void tuChoiKhoangNgayKhongHopLe() {
        PaymentReportService service = new PaymentReportService(transactions, vouchers, exporter);
        assertThrows(BusinessException.class,
                () -> service.report(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 16)));
        verifyNoInteractions(transactions, vouchers);
    }
}
