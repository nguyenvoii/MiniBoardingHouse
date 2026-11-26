package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group7.minihouse.shared.entity.*;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.storage.FileStorage;
import com.group7.minihouse.viet.dto.MaintenanceDtos.InvoiceAdjustmentRequest;
import com.group7.minihouse.viet.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MaintenanceInvoiceAdjustmentTest {

    private final MaintenanceRequestRepository requests = mock(MaintenanceRequestRepository.class);
    private final MaintenanceLogRepository logs = mock(MaintenanceLogRepository.class);
    private final NotificationSender notifications = mock(NotificationSender.class);
    private final EntityManager em = mock(EntityManager.class);
    private final MaintenanceService service = new MaintenanceService(requests, logs,
            mock(IncidentCategoryRepository.class), mock(TenantRoomResolver.class), notifications, mock(FileStorage.class));
    private final MaintenanceRequest request = new MaintenanceRequest();
    private final Invoice invoice = new Invoice();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "em", em);
        var room = new Room();
        room.setRoomId(5);
        var fee = new CalculatedMonthlyFee();
        fee.setRoom(room);
        fee.setTotalCalculatedAmount(new BigDecimal("1000"));
        request.setRoom(room);
        request.setStatus("RESOLVED");
        invoice.setInvoiceId(20);
        invoice.setInvoiceCode("INV20");
        invoice.setCalculatedFee(fee);
        invoice.setTenant(tenant(12));
        invoice.setTotalAmount(new BigDecimal("1000"));
        invoice.setPaymentStatus("UNPAID");
        when(requests.findById(3)).thenReturn(Optional.of(request));
        when(em.find(Invoice.class, 20, LockModeType.PESSIMISTIC_WRITE)).thenReturn(invoice);
    }

    @Test
    void rejectsDifferentTenantInSameRoom() {
        request.setTenant(tenant(11));

        assertThrows(BusinessException.class, () -> service.adjustInvoice(3,
                new InvoiceAdjustmentRequest(20, new BigDecimal("100"), "Meter correction")));

        assertEquals(new BigDecimal("1000"), invoice.getTotalAmount());
        verifyNoInteractions(logs, notifications);
    }

    @Test
    void staffRecordedIssueUpdatesBillAndNotifiesInvoiceTenant() {
        var log = new MaintenanceLog();
        when(logs.findFirstByRequest_RequestIdOrderByMaintenanceLogIdDesc(3)).thenReturn(Optional.of(log));

        var result = service.adjustInvoice(3,
                new InvoiceAdjustmentRequest(20, new BigDecimal("100"), "Meter correction"));

        assertEquals(new BigDecimal("900"), result.newTotal());
        assertEquals(new BigDecimal("900"), invoice.getCalculatedFee().getTotalCalculatedAmount());
        assertTrue(log.getResolutionNotes().contains("INV20"));
        verify(em).persist(argThat(item -> item instanceof FeeItemDetail line
                && new BigDecimal("-100.00").equals(line.getSubtotal())));
        verify(notifications).notifyTenant(eq(12), eq("INVOICE_ADJUSTED"), anyString(),
                anyString(), anyMap(), eq("INVOICE"), eq(20));
    }

    private static Tenant tenant(int id) {
        var tenant = new Tenant();
        tenant.setTenantId(id);
        return tenant;
    }
}
