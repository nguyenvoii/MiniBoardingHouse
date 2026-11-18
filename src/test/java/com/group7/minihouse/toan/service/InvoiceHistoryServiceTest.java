package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.toan.repository.InvoiceHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceHistoryServiceTest {
    @Mock InvoiceHistoryRepository repository;

    @Test
    void tuChoiLoaiThaoTacKhongHoTro() {
        InvoiceHistoryService service = new InvoiceHistoryService(repository);
        assertThrows(BusinessException.class, () -> service.search(null, null, "DELETE_ALL", null, 0, 20));
        verifyNoInteractions(repository);
    }

    @Test
    void traDanhSachRongKhiChuaCoLichSu() {
        when(repository.search(anyList(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(List.of());
        InvoiceHistoryService service = new InvoiceHistoryService(repository);
        assertTrue(service.search(LocalDateTime.now().minusDays(1), LocalDateTime.now(), null, null, 0, 20).isEmpty());
    }
}
