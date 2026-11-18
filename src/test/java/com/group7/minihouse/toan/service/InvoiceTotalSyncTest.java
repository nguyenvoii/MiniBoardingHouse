package com.group7.minihouse.toan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.group7.minihouse.shared.entity.CalculatedMonthlyFee;
import com.group7.minihouse.shared.entity.FeeItemDetail;
import com.group7.minihouse.shared.entity.Invoice;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** M14/M16: doi tong hoa don thi dong chi tiet, bang tinh va trang thai cung doi, qua mot ham duy nhat. */
class InvoiceTotalSyncTest {

    private Invoice invoice(String total, String paid) {
        CalculatedMonthlyFee fee = new CalculatedMonthlyFee();
        fee.setTotalCalculatedAmount(new BigDecimal(total));
        fee.setDiscountAmount(BigDecimal.ZERO);
        fee.setSurchargeAmount(BigDecimal.ZERO);
        Invoice i = new Invoice();
        i.setCalculatedFee(fee);
        i.setTotalAmount(new BigDecimal(total));
        i.setPaidAmount(new BigDecimal(paid));
        i.setDueDate(LocalDate.now().plusDays(5));
        i.setPaymentStatus("UNPAID");
        return i;
    }

    @Test
    void reducingTotalAddsNegativeLineAndKeepsFeeInSync() {
        EntityManager em = mock(EntityManager.class);
        Invoice inv = invoice("3000000", "0");
        new InvoiceBalanceCalculator().setTotalAndSync(em, inv, new BigDecimal("2800000"), "Cong to sai", LocalDate.now());
        ArgumentCaptor<Object> line = ArgumentCaptor.forClass(Object.class);
        verify(em).persist(line.capture());
        FeeItemDetail d = (FeeItemDetail) line.getValue();
        assertEquals(0, new BigDecimal("-200000").compareTo(d.getSubtotal()));
        assertEquals(0, new BigDecimal("2800000").compareTo(inv.getCalculatedFee().getTotalCalculatedAmount()));
        assertEquals(0, new BigDecimal("200000").compareTo(inv.getCalculatedFee().getDiscountAmount()));
        assertEquals("UNPAID", inv.getPaymentStatus());
    }

    @Test
    void reducingBelowPaidMarksInvoicePaidThroughTheCalculator() {
        EntityManager em = mock(EntityManager.class);
        Invoice inv = invoice("3000000", "2500000");
        new InvoiceBalanceCalculator().setTotalAndSync(em, inv, new BigDecimal("2500000"), "x", LocalDate.now());
        assertEquals("PAID", inv.getPaymentStatus());
    }

    @Test
    void sameTotalWritesNoLine() {
        EntityManager em = mock(EntityManager.class);
        new InvoiceBalanceCalculator().setTotalAndSync(em, invoice("3000000", "0"), new BigDecimal("3000000"), "x",
                LocalDate.now());
        org.mockito.Mockito.verify(em, org.mockito.Mockito.never()).persist(any());
    }
}
