package com.group7.minihouse.viet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.group7.minihouse.shared.entity.Room;
import com.group7.minihouse.shared.entity.Tenant;
import com.group7.minihouse.shared.entity.TenantDebt;
import com.group7.minihouse.viet.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DebtReminderDeliveryTest {

    @Test
    void sendsEachStageAndDoesNotRecordUndeliverableReminder() {
        var debts = mock(TenantDebtRepository.class);
        var reminders = mock(DebtReminderRepository.class);
        var notifications = mock(NotificationSender.class);
        var service = new DebtService(debts, mock(DebtPenaltyLogRepository.class), reminders,
                mock(DebtProcessingLogRepository.class), mock(ActorResolver.class), notifications);
        ReflectionTestUtils.setField(service, "em", mock(EntityManager.class));

        var first = debt(1, 1);
        var second = debt(2, 7);
        var finalWarning = debt(3, 15);
        var undeliverable = debt(4, 7);
        var alreadySent = debt(5, 1);
        when(debts.findByDebtStatusNotOrderByDebtId("CLEARED"))
                .thenReturn(List.of(first, second, finalWarning, undeliverable, alreadySent));
        when(reminders.existsByDebt_DebtIdAndReminderLevelAndSentAtAfter(eq(5), eq("FIRST_REMINDER"), any()))
                .thenReturn(true);
        when(notifications.notifyTenant(eq(20), anyString(), anyString(), anyString(), anyMap(), eq("DEBT"), intThat(id -> id < 4)))
                .thenReturn(true);

        assertEquals(3, service.sendReminders().sent());

        verify(notifications).notifyTenant(eq(20), eq("DEBT_REMINDER_1"), anyString(), anyString(), anyMap(), eq("DEBT"), eq(1));
        verify(notifications).notifyTenant(eq(20), eq("DEBT_REMINDER_2"), anyString(), anyString(), anyMap(), eq("DEBT"), eq(2));
        verify(notifications).notifyTenant(eq(20), eq("DEBT_FINAL_WARNING"), anyString(), anyString(), anyMap(), eq("DEBT"), eq(3));
        verify(notifications, never()).notifyTenant(anyInt(), anyString(), anyString(), anyString(), anyMap(), anyString(), eq(5));
        verify(reminders, times(3)).save(any());
    }

    private static TenantDebt debt(int id, int overdueDays) {
        var tenant = new Tenant();
        tenant.setTenantId(20);
        var room = new Room();
        room.setRoomCode("P101");
        var debt = new TenantDebt();
        debt.setDebtId(id);
        debt.setTenant(tenant);
        debt.setRoom(room);
        debt.setOverdueDays(overdueDays);
        debt.setTotalOutstandingDebt(new BigDecimal("1000"));
        return debt;
    }
}
