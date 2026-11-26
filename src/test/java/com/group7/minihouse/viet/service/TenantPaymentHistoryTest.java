package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group7.minihouse.shared.entity.PartialPaymentLog;
import com.group7.minihouse.shared.entity.PaymentTransaction;
import com.group7.minihouse.shared.entity.PrepaymentDeduction;
import com.group7.minihouse.shared.exception.BusinessException;
import com.group7.minihouse.viet.dto.DebtDtos.PaymentHistoryItem;
import com.group7.minihouse.viet.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class TenantPaymentHistoryTest {

    private final EntityManager em = mock(EntityManager.class);
    private final DebtService service = new DebtService(mock(TenantDebtRepository.class),
            mock(DebtPenaltyLogRepository.class), mock(DebtReminderRepository.class),
            mock(DebtProcessingLogRepository.class), mock(ActorResolver.class), mock(NotificationSender.class));

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
    }

    @Test
    void foreignInvoiceStopsBeforeLoadingPaymentRows() {
        query(Integer.class, List.of());

        BusinessException failure = assertThrows(BusinessException.class, () -> service.myPayments(2, 10));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatus());
        verify(em).createQuery(contains("i.tenant.tenantId = :tenant"), eq(Integer.class));
        verifyNoMoreInteractions(em);
    }

    @Test
    void returnsAllPaymentSourcesNewestFirst() {
        var time = LocalDateTime.of(2026, 11, 25, 12, 0);
        var tx = new PaymentTransaction();
        tx.setTransactionId(11);
        tx.setStatus("REVERSED");
        tx.setPaymentMethod("CASH");
        tx.setAmountPaid(BigDecimal.TEN);
        tx.setPaymentDate(time);
        var installment = new PartialPaymentLog();
        installment.setPartialLogId(12);
        installment.setAmount(BigDecimal.ONE);
        installment.setPaidDate(time.plusDays(1));
        var deduction = new PrepaymentDeduction();
        deduction.setDeductionId(13);
        deduction.setDeductedAmount(BigDecimal.TEN);
        deduction.setDeductedAt(time.plusDays(2));
        query(Integer.class, List.of(10));
        query(PaymentTransaction.class, List.of(tx));
        query(PartialPaymentLog.class, List.of(installment));
        query(PrepaymentDeduction.class, List.of(deduction));

        List<PaymentHistoryItem> result = service.myPayments(2, 10);

        assertEquals(List.of("PREPAYMENT", "INSTALLMENT", "TRANSACTION"),
                result.stream().map(PaymentHistoryItem::source).toList());
        assertEquals("REVERSED", result.get(2).status());
    }

    @SuppressWarnings("unchecked")
    private <T> void query(Class<T> entityType, List<T> result) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(em.createQuery(contains(".tenant.tenantId = :tenant"), eq(entityType))).thenReturn(query);
        when(query.setParameter("invoice", 10)).thenReturn(query);
        when(query.setParameter("tenant", 2)).thenReturn(query);
        when(query.getResultList()).thenReturn(result);
    }
}
