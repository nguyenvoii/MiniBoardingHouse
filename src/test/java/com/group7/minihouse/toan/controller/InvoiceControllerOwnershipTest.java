package com.group7.minihouse.toan.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.shared.security.AuthUser;
import com.group7.minihouse.toan.dto.InvoiceDtos.InvoiceResponse;
import com.group7.minihouse.toan.service.InvoiceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Nguoi thue chi doc duoc hoa don cua chinh minh; nhan vien doc duoc het; khong co principal (che do dev) van chay. */
class InvoiceControllerOwnershipTest {

    InvoiceService service = mock(InvoiceService.class);
    InvoiceController controller = new InvoiceController(service);
    AuthUser tenant3 = new AuthUser(30, "t@x", "A", List.of("TENANT"), 3);
    AuthUser staff = new AuthUser(1, "a@x", "Admin", List.of("MANAGER"), null);

    @BeforeEach
    void invoiceOfTenant3() {
        InvoiceResponse r = new InvoiceResponse(10, "INV-1", 1, 3, "A", 7, "P101", "2026-11", null, null, null, null,
                null, "UNPAID", null, null);
        when(service.get(10)).thenReturn(r);
        when(service.get(11)).thenReturn(new InvoiceResponse(11, "INV-2", 1, 4, "B", 8, "P102", "2026-11", null, null,
                null, null, null, "UNPAID", null, null));
    }

    @Test
    void tenantReadsOwnInvoiceDetail() {
        controller.detail(10, tenant3);
        verify(service).detail(10);
    }

    @Test
    void tenantCannotReadSomeoneElsesInvoiceDetailPrintOrNotifications() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(BusinessException.class, () -> controller.detail(11, tenant3)).getStatus());
        assertThrows(BusinessException.class, () -> controller.print(11, tenant3));
        assertThrows(BusinessException.class, () -> controller.notifications(11, tenant3));
        assertThrows(BusinessException.class, () -> controller.get(11, tenant3));
    }

    @Test
    void tenantSearchAndPayableAreForcedToOwnTenantId() {
        controller.search(null, null, null, null, 4, null, tenant3); // thu xem khach 4
        verify(service).search(null, null, null, null, 3, null);
        controller.payable(4, tenant3);
        verify(service).payable(3);
    }

    @Test
    void staffAndDevModeReadAnyInvoice() {
        controller.detail(11, staff);
        controller.detail(11, null);
        verify(service, org.mockito.Mockito.times(2)).detail(11);
    }
}
