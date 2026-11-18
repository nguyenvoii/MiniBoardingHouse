package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.export.TableExporter;
import com.group7.minihouse.toan.repository.MonthlyStatementRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonthlyStatementServiceTest {
    @Mock MonthlyStatementRepository repository;
    @Mock TableExporter exporter;

    @Test
    void taoBangKeThangKhongCoDuLieu() {
        when(repository.findInvoices(any(), any())).thenReturn(List.of());
        when(repository.findReceipts(any(), any())).thenReturn(List.of());
        MonthlyStatementService service = new MonthlyStatementService(repository, exporter);
        var statement = service.get("2026-11");
        assertEquals(0L, statement.summary().invoiceCount());
        assertEquals(0L, statement.summary().receiptCount());
        assertTrue(statement.invoices().isEmpty());
        assertTrue(statement.receipts().isEmpty());
    }

    @Test
    void tuChoiThangSaiDinhDang() {
        MonthlyStatementService service = new MonthlyStatementService(repository, exporter);
        assertThrows(BusinessException.class, () -> service.get("11/2026"));
        verifyNoInteractions(repository);
    }
}
